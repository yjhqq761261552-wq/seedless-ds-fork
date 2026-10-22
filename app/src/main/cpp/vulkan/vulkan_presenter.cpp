// SPDX-License-Identifier: GPL-3.0-or-later
// SeedlessDS CPU-frame Vulkan presenter. See VULKAN_LOW_LATENCY.md for the
// WatermelonDS reference and the deliberately different core integration.
#define VK_NO_PROTOTYPES
#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <jni.h>
#include <dlfcn.h>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <stdexcept>
#include <vector>
#include <utility>
#include <set>
#include "seedlessds/video_snapshot.h"
#include "present_policy.h"
#include "filter_policy.h"
#include "presenter_shaders.h"
#include "shared_frames.h"
#include "seedlessds/metrics.h"
#include <array>
#include <time.h>

#define VK_INSTANCE_FUNCTIONS(X) \
 X(DestroyInstance) X(EnumeratePhysicalDevices) X(GetPhysicalDeviceQueueFamilyProperties) \
 X(GetPhysicalDeviceSurfaceSupportKHR) X(GetPhysicalDeviceSurfaceCapabilitiesKHR) \
 X(GetPhysicalDeviceSurfaceFormatsKHR) X(GetPhysicalDeviceSurfacePresentModesKHR) \
 X(GetPhysicalDeviceMemoryProperties) X(GetPhysicalDeviceFormatProperties) \
 X(EnumerateDeviceExtensionProperties) X(CreateDevice) X(GetDeviceProcAddr) \
 X(CreateAndroidSurfaceKHR) X(DestroySurfaceKHR)
#define VK_DEVICE_FUNCTIONS(X) \
 X(DestroyDevice) X(DeviceWaitIdle) X(GetDeviceQueue) X(CreateSwapchainKHR) \
 X(DestroySwapchainKHR) X(GetSwapchainImagesKHR) X(AcquireNextImageKHR) X(QueuePresentKHR) \
 X(CreateBuffer) X(DestroyBuffer) X(GetBufferMemoryRequirements) X(AllocateMemory) \
 X(FreeMemory) X(BindBufferMemory) X(MapMemory) X(UnmapMemory) \
 X(CreateImage) X(DestroyImage) X(GetImageMemoryRequirements) X(BindImageMemory) \
 X(CreateImageView) X(DestroyImageView) X(CreateSampler) X(DestroySampler) \
 X(CreateCommandPool) X(DestroyCommandPool) X(AllocateCommandBuffers) X(ResetCommandBuffer) \
 X(BeginCommandBuffer) X(EndCommandBuffer) X(CmdPipelineBarrier) X(CmdCopyBufferToImage) \
 X(CreateFence) X(DestroyFence) X(WaitForFences) X(GetFenceStatus) X(ResetFences) X(CreateSemaphore) X(DestroySemaphore) \
 X(QueueSubmit) X(CreateRenderPass) X(DestroyRenderPass) X(CreateFramebuffer) X(DestroyFramebuffer) \
 X(CreateDescriptorSetLayout) X(DestroyDescriptorSetLayout) X(CreateDescriptorPool) \
 X(DestroyDescriptorPool) X(AllocateDescriptorSets) X(UpdateDescriptorSets) \
 X(CreateShaderModule) X(DestroyShaderModule) X(CreatePipelineLayout) X(DestroyPipelineLayout) \
 X(CreateGraphicsPipelines) X(DestroyPipeline) X(CmdBeginRenderPass) X(CmdEndRenderPass) \
 X(CmdBindPipeline) X(CmdBindDescriptorSets) X(CmdPushConstants) X(CmdDraw) X(CmdSetViewport) X(CmdSetScissor)

namespace {
seedless_vk::SharedFrames sharedFrames;
void check(VkResult result, const char *what) {
    if (result != VK_SUCCESS) {
        char msg[160]; std::snprintf(msg,sizeof msg,"%s: VkResult %d",what,result);
        throw std::runtime_error(msg);
    }
}
#define CHECK(call) check((call), #call)
template<class T> T info(VkStructureType type) { T result{}; result.sType=type; return result; }
struct Push {
    float rect[4], uv[4], clamp[4], alpha;
    int32_t rotation;
    int32_t filterMode;
    float padding;
};
static_assert(sizeof(Push)==64, "push constant layout");

class Presenter {
    void *loader = nullptr;
    PFN_vkGetInstanceProcAddr getInstance = nullptr;
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physical = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t queueFamily = 0;
    ANativeWindow *window = nullptr;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkExtent2D extent{};
    uint32_t logicalWidth=0, logicalHeight=0, atlasWidth=0, screenHeight=0;
    int rotate=0;
    bool external=false, registeredExternal=false;
    int filterMode=0;
    bool lowLatency=false;
    VkPresentModeKHR presentMode = VK_PRESENT_MODE_FIFO_KHR;
    VkFormat format = VK_FORMAT_UNDEFINED;
    std::set<std::pair<int,int>> failedCombinations;
    VkBuffer staging = VK_NULL_HANDLE;
    VkDeviceMemory stagingMemory = VK_NULL_HANDLE;
    void *mapped = nullptr;
    VkDeviceSize stagingBytes = 0;
    VkImage atlas = VK_NULL_HANDLE;
    VkDeviceMemory atlasMemory = VK_NULL_HANDLE;
    VkImageView atlasView = VK_NULL_HANDLE;
    VkSampler sampler = VK_NULL_HANDLE;
    VkDescriptorSetLayout descriptorLayout = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
    VkDescriptorSet descriptor = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkRenderPass renderPass = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkCommandBuffer command = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    VkSemaphore acquired = VK_NULL_HANDLE;
    std::vector<VkImage> images;
    std::vector<VkImageView> views;
    std::vector<VkFramebuffer> framebuffers;
    // Present waits can outlive submission fences. Reuse only the semaphore of
    // an image returned by AcquireNextImage, never a frame-indexed semaphore.
    std::vector<VkSemaphore> rendered;
    struct RetiredSwapchain { VkSwapchainKHR handle; std::vector<VkSemaphore> semaphores; };
    std::vector<RetiredSwapchain> retired;
    int retirementImage=-1;
    bool releaseRetiredAfterSubmit=false;
    void releaseRetired() {
        for (auto &old:retired) {
            for (auto semaphore:old.semaphores) vkDestroySemaphore(device,semaphore,nullptr);
            vkDestroySwapchainKHR(device,old.handle,nullptr);
        }
        retired.clear();
    }
    bool atlasInitialized=false, submitted=false;
    uint64_t lastSerial=0;
    unsigned transformPoll=0;
    std::array<uint64_t,256> frameAges{}, cpuTimes{};
    unsigned statSamples=0;
    uint64_t skippedFrames=0, acquisitionMisses=0;
    uint64_t percentile(const std::array<uint64_t,256> &values, unsigned p) {
        auto sorted=values;
        unsigned count=std::min(statSamples,256u);
        if (!count) return 0;
        std::sort(sorted.begin(),sorted.begin()+count);
        return sorted[(count*p+99)/100-1];
    }
    nds_video_snapshot_t snapshot{};
    float previousRects[10]{};
    bool previousOrder=false;
    bool hasRects=false;
    bool linear=false;
#define DECLARE(name) PFN_vk##name vk##name = nullptr;
    VK_INSTANCE_FUNCTIONS(DECLARE)
    VK_DEVICE_FUNCTIONS(DECLARE)
#undef DECLARE

    uint32_t memoryType(uint32_t bits, VkMemoryPropertyFlags flags) {
        VkPhysicalDeviceMemoryProperties props{};
        vkGetPhysicalDeviceMemoryProperties(physical,&props);
        for (uint32_t i=0; i<props.memoryTypeCount; ++i)
            if ((bits&(1u<<i)) && (props.memoryTypes[i].propertyFlags&flags)==flags) return i;
        throw std::runtime_error("Required Vulkan memory type is unavailable");
    }
    VkDeviceMemory allocate(const VkMemoryRequirements &req, VkMemoryPropertyFlags flags) {
        auto allocation=info<VkMemoryAllocateInfo>(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        allocation.allocationSize=req.size; allocation.memoryTypeIndex=memoryType(req.memoryTypeBits,flags);
        VkDeviceMemory memory{}; CHECK(vkAllocateMemory(device,&allocation,nullptr,&memory)); return memory;
    }
    VkImageView imageView(VkImage image, VkFormat f) {
        auto ci=info<VkImageViewCreateInfo>(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
        ci.image=image; ci.viewType=VK_IMAGE_VIEW_TYPE_2D; ci.format=f;
        ci.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        VkImageView view{}; CHECK(vkCreateImageView(device,&ci,nullptr,&view)); return view;
    }
    VkShaderModule shader(const uint32_t *code, size_t bytes) {
        auto ci=info<VkShaderModuleCreateInfo>(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
        ci.codeSize=bytes; ci.pCode=code;
        VkShaderModule result{}; CHECK(vkCreateShaderModule(device,&ci,nullptr,&result)); return result;
    }
    void makePipeline() {
        VkAttachmentDescription attachment{};
        attachment.format=format; attachment.samples=VK_SAMPLE_COUNT_1_BIT;
        attachment.loadOp=VK_ATTACHMENT_LOAD_OP_CLEAR; attachment.storeOp=VK_ATTACHMENT_STORE_OP_STORE;
        attachment.stencilLoadOp=VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        attachment.stencilStoreOp=VK_ATTACHMENT_STORE_OP_DONT_CARE;
        attachment.initialLayout=VK_IMAGE_LAYOUT_UNDEFINED;
        attachment.finalLayout=VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        VkAttachmentReference color{0,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
        VkSubpassDescription subpass{}; subpass.pipelineBindPoint=VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount=1; subpass.pColorAttachments=&color;
        VkSubpassDependency dependency{};
        dependency.srcSubpass=VK_SUBPASS_EXTERNAL; dependency.dstSubpass=0;
        dependency.srcStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstStageMask=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstAccessMask=VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        auto rp=info<VkRenderPassCreateInfo>(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
        rp.attachmentCount=1; rp.pAttachments=&attachment; rp.subpassCount=1; rp.pSubpasses=&subpass;
        rp.dependencyCount=1; rp.pDependencies=&dependency;
        CHECK(vkCreateRenderPass(device,&rp,nullptr,&renderPass));
        VkDescriptorSetLayoutBinding binding{0,VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,1,VK_SHADER_STAGE_FRAGMENT_BIT,nullptr};
        auto dl=info<VkDescriptorSetLayoutCreateInfo>(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
        dl.bindingCount=1; dl.pBindings=&binding;
        CHECK(vkCreateDescriptorSetLayout(device,&dl,nullptr,&descriptorLayout));
        VkPushConstantRange range{VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,0,sizeof(Push)};
        auto pl=info<VkPipelineLayoutCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
        pl.setLayoutCount=1; pl.pSetLayouts=&descriptorLayout; pl.pushConstantRangeCount=1; pl.pPushConstantRanges=&range;
        CHECK(vkCreatePipelineLayout(device,&pl,nullptr,&pipelineLayout));
        VkShaderModule vert=shader(presenter_vert,sizeof presenter_vert), frag=VK_NULL_HANDLE;
        try {
            frag=shader(presenter_frag,sizeof presenter_frag);
            VkPipelineShaderStageCreateInfo stages[2]{};
            for (auto &stage:stages) { stage.sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO; stage.pName="main"; }
            stages[0].stage=VK_SHADER_STAGE_VERTEX_BIT; stages[0].module=vert;
            stages[1].stage=VK_SHADER_STAGE_FRAGMENT_BIT; stages[1].module=frag;
            auto vertex=info<VkPipelineVertexInputStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            auto assembly=info<VkPipelineInputAssemblyStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
            assembly.topology=VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            auto viewport=info<VkPipelineViewportStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
            viewport.viewportCount=1; viewport.scissorCount=1;
            auto raster=info<VkPipelineRasterizationStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
            raster.polygonMode=VK_POLYGON_MODE_FILL; raster.cullMode=VK_CULL_MODE_NONE;
            raster.frontFace=VK_FRONT_FACE_COUNTER_CLOCKWISE; raster.lineWidth=1;
            auto ms=info<VkPipelineMultisampleStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
            ms.rasterizationSamples=VK_SAMPLE_COUNT_1_BIT;
            VkPipelineColorBlendAttachmentState blend{};
            blend.blendEnable=VK_TRUE; blend.srcColorBlendFactor=VK_BLEND_FACTOR_SRC_ALPHA;
            blend.dstColorBlendFactor=VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA; blend.colorBlendOp=VK_BLEND_OP_ADD;
            blend.srcAlphaBlendFactor=VK_BLEND_FACTOR_ONE; blend.dstAlphaBlendFactor=VK_BLEND_FACTOR_ZERO;
            blend.alphaBlendOp=VK_BLEND_OP_ADD; blend.colorWriteMask=0xf;
            auto blending=info<VkPipelineColorBlendStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
            blending.attachmentCount=1; blending.pAttachments=&blend;
            VkDynamicState states[]={VK_DYNAMIC_STATE_VIEWPORT,VK_DYNAMIC_STATE_SCISSOR};
            auto dynamic=info<VkPipelineDynamicStateCreateInfo>(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO);
            dynamic.dynamicStateCount=2; dynamic.pDynamicStates=states;
            auto pi=info<VkGraphicsPipelineCreateInfo>(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
            pi.stageCount=2; pi.pStages=stages; pi.pVertexInputState=&vertex; pi.pInputAssemblyState=&assembly;
            pi.pViewportState=&viewport; pi.pRasterizationState=&raster; pi.pMultisampleState=&ms;
            pi.pColorBlendState=&blending; pi.pDynamicState=&dynamic; pi.layout=pipelineLayout; pi.renderPass=renderPass;
            CHECK(vkCreateGraphicsPipelines(device,VK_NULL_HANDLE,1,&pi,nullptr,&pipeline));
        } catch (...) {
            if (frag) vkDestroyShaderModule(device,frag,nullptr);
            vkDestroyShaderModule(device,vert,nullptr); throw;
        }
        vkDestroyShaderModule(device,frag,nullptr); vkDestroyShaderModule(device,vert,nullptr);
    }
    void makeUploadResources(unsigned ceiling) {
        atlasWidth=256*ceiling; screenHeight=192*ceiling;
        stagingBytes=(VkDeviceSize)atlasWidth*screenHeight*8;
        auto buffer=info<VkBufferCreateInfo>(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        buffer.size=stagingBytes; buffer.usage=VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
        CHECK(vkCreateBuffer(device,&buffer,nullptr,&staging));
        VkMemoryRequirements req{}; vkGetBufferMemoryRequirements(device,staging,&req);
        stagingMemory=allocate(req,VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        CHECK(vkBindBufferMemory(device,staging,stagingMemory,0));
        CHECK(vkMapMemory(device,stagingMemory,0,stagingBytes,0,&mapped));
        auto image=info<VkImageCreateInfo>(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
        image.imageType=VK_IMAGE_TYPE_2D; image.format=VK_FORMAT_R8G8B8A8_UNORM;
        image.extent={atlasWidth,screenHeight*2,1}; image.mipLevels=1; image.arrayLayers=1;
        image.samples=VK_SAMPLE_COUNT_1_BIT; image.tiling=VK_IMAGE_TILING_OPTIMAL;
        image.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT;
        CHECK(vkCreateImage(device,&image,nullptr,&atlas));
        vkGetImageMemoryRequirements(device,atlas,&req);
        atlasMemory=allocate(req,VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        CHECK(vkBindImageMemory(device,atlas,atlasMemory,0));
        atlasView=imageView(atlas,VK_FORMAT_R8G8B8A8_UNORM);
        auto si=info<VkSamplerCreateInfo>(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);
        si.magFilter=si.minFilter=linear?VK_FILTER_LINEAR:VK_FILTER_NEAREST;
        si.mipmapMode=VK_SAMPLER_MIPMAP_MODE_NEAREST;
        si.addressModeU=si.addressModeV=si.addressModeW=VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        si.maxLod=0;
        CHECK(vkCreateSampler(device,&si,nullptr,&sampler));
        VkDescriptorPoolSize size{VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,1};
        auto dp=info<VkDescriptorPoolCreateInfo>(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
        dp.maxSets=1; dp.poolSizeCount=1; dp.pPoolSizes=&size;
        CHECK(vkCreateDescriptorPool(device,&dp,nullptr,&descriptorPool));
        auto da=info<VkDescriptorSetAllocateInfo>(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
        da.descriptorPool=descriptorPool; da.descriptorSetCount=1; da.pSetLayouts=&descriptorLayout;
        CHECK(vkAllocateDescriptorSets(device,&da,&descriptor));
        VkDescriptorImageInfo di{sampler,atlasView,VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
        auto write=info<VkWriteDescriptorSet>(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
        write.dstSet=descriptor; write.dstBinding=0; write.descriptorCount=1;
        write.descriptorType=VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER; write.pImageInfo=&di;
        vkUpdateDescriptorSets(device,1,&write,0,nullptr);
        auto cp=info<VkCommandPoolCreateInfo>(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
        cp.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT; cp.queueFamilyIndex=queueFamily;
        CHECK(vkCreateCommandPool(device,&cp,nullptr,&commandPool));
        auto ca=info<VkCommandBufferAllocateInfo>(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
        ca.commandPool=commandPool; ca.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY; ca.commandBufferCount=1;
        CHECK(vkAllocateCommandBuffers(device,&ca,&command));
        auto fi=info<VkFenceCreateInfo>(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO); fi.flags=VK_FENCE_CREATE_SIGNALED_BIT;
        CHECK(vkCreateFence(device,&fi,nullptr,&fence));
        auto sem=info<VkSemaphoreCreateInfo>(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        CHECK(vkCreateSemaphore(device,&sem,nullptr,&acquired));
    }
    void makeSwapchain(unsigned width, unsigned height) {
        CHECK(vkDeviceWaitIdle(device));
        // GPU idle alone does not prove that presentation wait semaphores
        // are unused. Retire them until the first new presentation is acquired
        // again and its acquire dependency has completed (Khronos WSI sample).
        if (swapchain) {
            if (retired.size()>=4) throw std::runtime_error("Repeated surface changes without completed presentation");
            retired.push_back({swapchain,std::move(rendered)});
            rendered.clear();
            swapchain=VK_NULL_HANDLE;
        }
        destroySwapchain();
        retirementImage=-1; releaseRetiredAfterSubmit=false;
        VkSurfaceCapabilitiesKHR caps{};
        CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical,surface,&caps));
        rotate=seedless_vk::rotation(caps.currentTransform);
        if (rotate<0) throw std::runtime_error("Mirrored Vulkan surface transform is unsupported");
        logicalWidth=std::clamp(width,caps.minImageExtent.width,caps.maxImageExtent.width);
        logicalHeight=std::clamp(height,caps.minImageExtent.height,caps.maxImageExtent.height);
        extent={logicalWidth,logicalHeight};
        if (rotate==1 || rotate==3) std::swap(extent.width,extent.height);
        if (!(caps.supportedUsageFlags&VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)) throw std::runtime_error("Surface cannot be a color attachment");
        uint32_t count=0;
        CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical,surface,&count,nullptr));
        std::vector<VkSurfaceFormatKHR> formats(count);
        CHECK(vkGetPhysicalDeviceSurfaceFormatsKHR(physical,surface,&count,formats.data()));
        std::vector<VkSurfaceFormatKHR> candidates;
        for (VkFormat preferred : {VK_FORMAT_R8G8B8A8_UNORM,VK_FORMAT_B8G8R8A8_UNORM}) {
            for (auto f : formats) {
                if (f.format==VK_FORMAT_UNDEFINED) f.format=preferred;
                if (f.colorSpace==VK_COLOR_SPACE_SRGB_NONLINEAR_KHR && f.format==preferred
                    && (!renderPass || format==preferred)) { candidates.push_back(f); break; }
            }
        }
        if (candidates.empty()) throw std::runtime_error("No compatible UNORM surface format");
        CHECK(vkGetPhysicalDeviceSurfacePresentModesKHR(physical,surface,&count,nullptr));
        std::vector<VkPresentModeKHR> modes(count);
        CHECK(vkGetPhysicalDeviceSurfacePresentModesKHR(physical,surface,&count,modes.data()));
        VkCompositeAlphaFlagBitsKHR alpha=VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        if (!(caps.supportedCompositeAlpha&alpha)) {
            const VkCompositeAlphaFlagBitsKHR options[]={VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR,
                VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR};
            for (auto a:options) if (caps.supportedCompositeAlpha&a) {alpha=a;break;}
        }
        auto ci=info<VkSwapchainCreateInfoKHR>(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
        ci.surface=surface; ci.imageExtent=extent;
        ci.imageArrayLayers=1; ci.imageUsage=VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        if (!retired.empty()) ci.oldSwapchain=retired.back().handle;
        ci.preTransform=caps.currentTransform; ci.compositeAlpha=alpha; ci.clipped=VK_TRUE;
        VkResult creation=VK_ERROR_INITIALIZATION_FAILED;
        const auto ranked=seedless_vk::preferred_modes(lowLatency);
        for (auto candidate:candidates) {
            if (swapchain) break;
            for (auto mode:ranked) {
                if (std::find(modes.begin(),modes.end(),mode)==modes.end()) continue;
                const std::pair<int,int> key{(int)candidate.format,(int)mode};
                if (failedCombinations.count(key)) continue;
                ci.imageFormat=candidate.format; ci.imageColorSpace=candidate.colorSpace;
                ci.presentMode=mode;
                ci.minImageCount=seedless_vk::image_count(caps.minImageCount,caps.maxImageCount,mode);
                creation=vkCreateSwapchainKHR(device,&ci,nullptr,&swapchain);
                if (creation==VK_SUCCESS) { format=candidate.format; presentMode=mode; break; }
                swapchain=VK_NULL_HANDLE;
                // Surface loss/out-of-date is a lifecycle event, not evidence
                // that a format/mode pair is permanently unsupported.
                if (creation==VK_ERROR_SURFACE_LOST_KHR || creation==VK_ERROR_OUT_OF_DATE_KHR)
                    check(creation,"Surface changed during swapchain creation");
                failedCombinations.insert(key);
                __android_log_print(ANDROID_LOG_WARN,"SeedlessVulkan","Retrying swapchain after format=%d mode=%d VkResult=%d",candidate.format,mode,creation);
            }
        }
        if (!swapchain) check(creation,"All compatible Vulkan swapchain combinations failed");
        CHECK(vkGetSwapchainImagesKHR(device,swapchain,&count,nullptr));
        images.resize(count); CHECK(vkGetSwapchainImagesKHR(device,swapchain,&count,images.data()));
        if (!renderPass) makePipeline();
        auto sem=info<VkSemaphoreCreateInfo>(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
        for (auto image:images) {
            views.push_back(imageView(image,format));
            auto fb=info<VkFramebufferCreateInfo>(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
            fb.renderPass=renderPass; fb.attachmentCount=1; fb.pAttachments=&views.back();
            fb.width=extent.width; fb.height=extent.height; fb.layers=1;
            VkFramebuffer target{}; CHECK(vkCreateFramebuffer(device,&fb,nullptr,&target)); framebuffers.push_back(target);
            VkSemaphore done{}; CHECK(vkCreateSemaphore(device,&sem,nullptr,&done)); rendered.push_back(done);
        }
        hasRects=false;
        __android_log_print(ANDROID_LOG_INFO,"SeedlessVulkan","presentMode=%d images=%u transform=%d logical=%ux%u extent=%ux%u maxInFlight=1 lowLatency=%d",
            presentMode,count,rotate,logicalWidth,logicalHeight,extent.width,extent.height,lowLatency?1:0);
    }
    void destroySwapchain() {
        for (auto f:framebuffers) vkDestroyFramebuffer(device,f,nullptr);
        for (auto v:views) vkDestroyImageView(device,v,nullptr);
        for (auto s:rendered) vkDestroySemaphore(device,s,nullptr);
        framebuffers.clear(); views.clear(); rendered.clear(); images.clear();
        if (swapchain) vkDestroySwapchainKHR(device,swapchain,nullptr);
        swapchain=VK_NULL_HANDLE;
    }
    void record(uint32_t image, const float *rects, bool bottomFirst) {
        CHECK(vkResetCommandBuffer(command,0));
        auto begin=info<VkCommandBufferBeginInfo>(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        begin.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        CHECK(vkBeginCommandBuffer(command,&begin));
        if (snapshot.serial) {
        auto barrier=info<VkImageMemoryBarrier>(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);
        barrier.srcAccessMask=atlasInitialized?VK_ACCESS_SHADER_READ_BIT:0;
        barrier.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;
        barrier.oldLayout=atlasInitialized?VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:VK_IMAGE_LAYOUT_UNDEFINED;
        barrier.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        barrier.srcQueueFamilyIndex=barrier.dstQueueFamilyIndex=VK_QUEUE_FAMILY_IGNORED;
        barrier.image=atlas; barrier.subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1};
        vkCmdPipelineBarrier(command,atlasInitialized?VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT:VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,nullptr,0,nullptr,1,&barrier);
        VkBufferImageCopy regions[2]{};
        unsigned regionCount=0;
        for (unsigned s=0;s<2;++s) {
            if (rects[s*4+2]<=0 || rects[s*4+3]<=0 || rects[8+s]<=0) continue;
            auto &region=regions[regionCount++];
            region.bufferOffset=snapshot.offset[s];
            region.imageSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1};
            region.imageOffset={0,(int32_t)(s*screenHeight),0};
            region.imageExtent={snapshot.width[s],snapshot.height[s],1};
        }
        if (regionCount) vkCmdCopyBufferToImage(command,staging,atlas,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,regionCount,regions);
        barrier.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT; barrier.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;
        barrier.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL; barrier.newLayout=VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,0,0,nullptr,0,nullptr,1,&barrier);
        }
        VkClearValue clear{}; clear.color.float32[3]=1;
        auto rp=info<VkRenderPassBeginInfo>(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
        rp.renderPass=renderPass; rp.framebuffer=framebuffers[image]; rp.renderArea.extent=extent;
        rp.clearValueCount=1; rp.pClearValues=&clear;
        vkCmdBeginRenderPass(command,&rp,VK_SUBPASS_CONTENTS_INLINE);
        VkViewport viewport{0,0,(float)extent.width,(float)extent.height,0,1};
        VkRect2D scissor{{0,0},extent};
        vkCmdSetViewport(command,0,1,&viewport); vkCmdSetScissor(command,0,1,&scissor);
        vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_GRAPHICS,pipeline);
        vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_GRAPHICS,pipelineLayout,0,1,&descriptor,0,nullptr);
        for (int order=0;snapshot.serial && order<2;++order) {
            unsigned s=bottomFirst?1-order:order;
            const float *r=rects+s*4;
            if (r[2]<=0 || r[3]<=0 || rects[8+s]<=0) continue;
            Push p{};
            p.rect[0]=r[0]/logicalWidth*2-1; p.rect[1]=r[1]/logicalHeight*2-1;
            p.rect[2]=r[2]/logicalWidth*2; p.rect[3]=r[3]/logicalHeight*2;
            p.uv[0]=0; p.uv[1]=(float)(s*screenHeight)/(screenHeight*2);
            p.uv[2]=(float)snapshot.width[s]/atlasWidth; p.uv[3]=(float)snapshot.height[s]/(screenHeight*2);
            p.clamp[0]=.5f/atlasWidth; p.clamp[1]=(s*screenHeight+.5f)/(screenHeight*2);
            p.clamp[2]=(snapshot.width[s]-.5f)/atlasWidth; p.clamp[3]=(s*screenHeight+snapshot.height[s]-.5f)/(screenHeight*2);
            p.alpha=std::clamp(rects[8+s],0.f,1.f); p.rotation=rotate; p.filterMode=filterMode;
            vkCmdPushConstants(command,pipelineLayout,VK_SHADER_STAGE_VERTEX_BIT|VK_SHADER_STAGE_FRAGMENT_BIT,0,sizeof p,&p);
            vkCmdDraw(command,6,1,0,0);
        }
        vkCmdEndRenderPass(command); CHECK(vkEndCommandBuffer(command));
    }
public:
    ~Presenter() { shutdown(); }
    void init(ANativeWindow *ownedWindow, unsigned width, unsigned height, unsigned ceiling, bool smoothing, bool lowLatencyMode) {
        lowLatency=lowLatencyMode;
        window=ownedWindow; linear=smoothing;
        if (!window || !width || !height || ceiling<1 || ceiling>8) throw std::runtime_error("Invalid Vulkan window/scale");
        loader=dlopen("libvulkan.so",RTLD_NOW|RTLD_LOCAL);
        if (!loader) throw std::runtime_error("Vulkan loader is unavailable");
        getInstance=(PFN_vkGetInstanceProcAddr)dlsym(loader,"vkGetInstanceProcAddr");
        if (!getInstance) throw std::runtime_error("Vulkan entry point is unavailable");
        auto create=(PFN_vkCreateInstance)getInstance(VK_NULL_HANDLE,"vkCreateInstance");
        if (!create) throw std::runtime_error("Vulkan 1.0 is unavailable");
        const char *extensions[]={VK_KHR_SURFACE_EXTENSION_NAME,VK_KHR_ANDROID_SURFACE_EXTENSION_NAME};
        auto app=info<VkApplicationInfo>(VK_STRUCTURE_TYPE_APPLICATION_INFO); app.pApplicationName="SeedlessDS"; app.apiVersion=VK_API_VERSION_1_0;
        auto ci=info<VkInstanceCreateInfo>(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
        ci.pApplicationInfo=&app; ci.enabledExtensionCount=2; ci.ppEnabledExtensionNames=extensions;
        CHECK(create(&ci,nullptr,&instance));
#define LOAD_INSTANCE(name) vk##name=(PFN_vk##name)getInstance(instance,"vk" #name); if (!vk##name) throw std::runtime_error("Missing vk" #name);
        VK_INSTANCE_FUNCTIONS(LOAD_INSTANCE)
#undef LOAD_INSTANCE
        auto sc=info<VkAndroidSurfaceCreateInfoKHR>(VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR); sc.window=window;
        CHECK(vkCreateAndroidSurfaceKHR(instance,&sc,nullptr,&surface));
        uint32_t count=0; CHECK(vkEnumeratePhysicalDevices(instance,&count,nullptr));
        std::vector<VkPhysicalDevice> candidates(count); CHECK(vkEnumeratePhysicalDevices(instance,&count,candidates.data()));
        for (auto candidate:candidates) {
            uint32_t families=0; vkGetPhysicalDeviceQueueFamilyProperties(candidate,&families,nullptr);
            std::vector<VkQueueFamilyProperties> properties(families);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate,&families,properties.data());
            for (uint32_t family=0;family<families;++family) {
                VkBool32 supported=VK_FALSE;
                if (vkGetPhysicalDeviceSurfaceSupportKHR(candidate,family,surface,&supported)==VK_SUCCESS && supported
                    && properties[family].queueCount && (properties[family].queueFlags&VK_QUEUE_GRAPHICS_BIT)) {
                    physical=candidate; queueFamily=family; break;
                }
            }
            if (physical) break;
        }
        if (!physical) throw std::runtime_error("No Vulkan graphics/present queue");
        VkFormatProperties fp{}; vkGetPhysicalDeviceFormatProperties(physical,VK_FORMAT_R8G8B8A8_UNORM,&fp);
        VkFormatFeatureFlags required=VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT;
        if (linear) required|=VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
        if ((fp.optimalTilingFeatures&required)!=required) throw std::runtime_error("RGBA texture format is unsupported");
        float priority=1;
        auto qi=info<VkDeviceQueueCreateInfo>(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
        qi.queueFamilyIndex=queueFamily; qi.queueCount=1; qi.pQueuePriorities=&priority;
        const char *swapExtension=VK_KHR_SWAPCHAIN_EXTENSION_NAME;
        auto di=info<VkDeviceCreateInfo>(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
        di.queueCreateInfoCount=1; di.pQueueCreateInfos=&qi; di.enabledExtensionCount=1; di.ppEnabledExtensionNames=&swapExtension;
        CHECK(vkCreateDevice(physical,&di,nullptr,&device));
#define LOAD_DEVICE(name) vk##name=(PFN_vk##name)vkGetDeviceProcAddr(device,"vk" #name); if (!vk##name) throw std::runtime_error("Missing vk" #name);
        VK_DEVICE_FUNCTIONS(LOAD_DEVICE)
#undef LOAD_DEVICE
        vkGetDeviceQueue(device,queueFamily,0,&queue);
        makeSwapchain(width,height); makeUploadResources(ceiling);
    }
    void setRole(bool secondary, int mode) {
        external=secondary; filterMode=mode;
        if (external) { sharedFrames.attach(); registeredExternal=true; }
    }
    void awaitFrame() {
        if (submitted && vkGetFenceStatus(device,fence)==VK_NOT_READY) {
            vkWaitForFences(device,1,&fence,VK_TRUE,1000000);
            return;
        }
        uint64_t latest=external?sharedFrames.serial():nds_video_latest_serial();
        if (latest==lastSerial) nds_video_wait_new_frame(nds_video_latest_serial(),16000);
        else {
            // A new packet is ready but WSI acquisition can still be busy.
            const timespec retry{0,1000000}; nanosleep(&retry,nullptr);
        }
    }
    int draw(const float *rects, bool bottomFirst) {
        uint64_t cpuStart=nds_metrics_now_us();
        if (submitted) {
            VkResult ready=vkGetFenceStatus(device,fence);
            if (ready==VK_NOT_READY) return 0;
            CHECK(ready); submitted=false;
            if (releaseRetiredAfterSubmit) {
                releaseRetired(); releaseRetiredAfterSubmit=false;
            }
        }
        bool changed=!hasRects || bottomFirst!=previousOrder || std::memcmp(rects,previousRects,sizeof previousRects)!=0;
        uint64_t availableSerial=external?sharedFrames.serial():nds_video_latest_serial();
        if (!changed && availableSerial==lastSerial) return 0;
        if (external && !availableSerial) return 0;
        uint32_t image=0;
        VkResult result=vkAcquireNextImageKHR(device,swapchain,0,acquired,VK_NULL_HANDLE,&image);
        if (result==VK_NOT_READY || result==VK_TIMEOUT) { ++acquisitionMisses; return 0; }
        if (result==VK_ERROR_OUT_OF_DATE_KHR) { makeSwapchain(logicalWidth,logicalHeight); return 0; }
        bool suboptimal=result==VK_SUBOPTIMAL_KHR;
        if (!suboptimal) CHECK(result);
        if (!retired.empty() && (int)image==retirementImage) releaseRetiredAfterSubmit=true;
        nds_video_snapshot_t newest{};
        if (external || sharedFrames.active()) {
            auto packet=sharedFrames.latest(external,(size_t)stagingBytes);
            if (packet) {
                snapshot=packet->info;
                if (snapshot.bytes>stagingBytes) throw std::runtime_error("Shared packet exceeds staging allocation");
                for (unsigned screen=0;screen<2;++screen) {
                    if (rects[screen*4+2]<=0 || rects[screen*4+3]<=0 || rects[8+screen]<=0) continue;
                    size_t bytes=(size_t)snapshot.width[screen]*snapshot.height[screen]*4;
                    std::memcpy((unsigned char *)mapped+snapshot.offset[screen],packet->rgba.data()+snapshot.offset[screen],bytes);
                }
            }
        } else if (nds_video_copy_latest(mapped,(size_t)stagingBytes,0,&newest)) snapshot=newest;
        if (snapshot.width[0]>atlasWidth || snapshot.width[1]>atlasWidth ||
            snapshot.height[0]>screenHeight || snapshot.height[1]>screenHeight)
            throw std::runtime_error("Frame exceeds Vulkan atlas");
        record(image,rects,bottomFirst);
        CHECK(vkResetFences(device,1,&fence));
        VkPipelineStageFlags waitStage=VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        auto submit=info<VkSubmitInfo>(VK_STRUCTURE_TYPE_SUBMIT_INFO);
        submit.waitSemaphoreCount=1; submit.pWaitSemaphores=&acquired; submit.pWaitDstStageMask=&waitStage;
        submit.commandBufferCount=1; submit.pCommandBuffers=&command;
        submit.signalSemaphoreCount=1; submit.pSignalSemaphores=&rendered[image];
        CHECK(vkQueueSubmit(queue,1,&submit,fence)); submitted=true; atlasInitialized=snapshot.serial!=0;
        auto present=info<VkPresentInfoKHR>(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
        present.waitSemaphoreCount=1; present.pWaitSemaphores=&rendered[image];
        present.swapchainCount=1; present.pSwapchains=&swapchain; present.pImageIndices=&image;
        result=vkQueuePresentKHR(queue,&present);
        if (result==VK_ERROR_OUT_OF_DATE_KHR) {
            makeSwapchain(logicalWidth,logicalHeight); return 0;
        }
        if (result!=VK_SUBOPTIMAL_KHR) CHECK(result);
        if (!retired.empty() && retirementImage<0) retirementImage=(int)image;
        bool rebuilt=false;
        if (suboptimal || result==VK_SUBOPTIMAL_KHR || ++transformPoll>=120) {
            transformPoll=0;
            VkSurfaceCapabilitiesKHR caps{};
            CHECK(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical,surface,&caps));
            unsigned width=(unsigned)std::max(ANativeWindow_getWidth(window),1);
            unsigned height=(unsigned)std::max(ANativeWindow_getHeight(window),1);
            if (seedless_vk::rotation(caps.currentTransform)!=rotate || width!=logicalWidth || height!=logicalHeight) {
                makeSwapchain(width,height); rebuilt=true;
            }
        }
        if (lastSerial && snapshot.serial>lastSerial+1) skippedFrames+=snapshot.serial-lastSerial-1;
        uint64_t now=nds_metrics_now_us();
        frameAges[statSamples%256]=snapshot.published_us && now>=snapshot.published_us ? now-snapshot.published_us : 0;
        cpuTimes[statSamples%256]=now-cpuStart;
        if (++statSamples%300==0) __android_log_print(ANDROID_LOG_INFO,"SeedlessVulkanStats",
            "%s serial=%llu cpuP95us=%llu cpuP99us=%llu ageP95us=%llu ageP99us=%llu skipped=%llu acquireMiss=%llu",
            external?"external":"primary",(unsigned long long)snapshot.serial,
            (unsigned long long)percentile(cpuTimes,95),(unsigned long long)percentile(cpuTimes,99),
            (unsigned long long)percentile(frameAges,95),(unsigned long long)percentile(frameAges,99),
            (unsigned long long)skippedFrames,(unsigned long long)acquisitionMisses);
        lastSerial=snapshot.serial; hasRects=!rebuilt; previousOrder=bottomFirst;
        std::memcpy(previousRects,rects,sizeof previousRects);
        return snapshot.serial ? 1 : 0;
    }
    void shutdown() noexcept {
        if (registeredExternal) { sharedFrames.detach(); registeredExternal=false; }
        if (device && vkDeviceWaitIdle) vkDeviceWaitIdle(device);
        if (device && vkDestroyDevice) {
            destroySwapchain();
            releaseRetired();
            if (mapped && vkUnmapMemory) vkUnmapMemory(device,stagingMemory);
            if (fence) vkDestroyFence(device,fence,nullptr);
            if (acquired) vkDestroySemaphore(device,acquired,nullptr);
            if (commandPool) vkDestroyCommandPool(device,commandPool,nullptr);
            if (pipeline) vkDestroyPipeline(device,pipeline,nullptr);
            if (pipelineLayout) vkDestroyPipelineLayout(device,pipelineLayout,nullptr);
            if (renderPass) vkDestroyRenderPass(device,renderPass,nullptr);
            if (descriptorPool) vkDestroyDescriptorPool(device,descriptorPool,nullptr);
            if (descriptorLayout) vkDestroyDescriptorSetLayout(device,descriptorLayout,nullptr);
            if (sampler) vkDestroySampler(device,sampler,nullptr);
            if (atlasView) vkDestroyImageView(device,atlasView,nullptr);
            if (atlas) vkDestroyImage(device,atlas,nullptr);
            if (atlasMemory) vkFreeMemory(device,atlasMemory,nullptr);
            if (staging) vkDestroyBuffer(device,staging,nullptr);
            if (stagingMemory) vkFreeMemory(device,stagingMemory,nullptr);
            vkDestroyDevice(device,nullptr);
        }
        if (surface && vkDestroySurfaceKHR) vkDestroySurfaceKHR(instance,surface,nullptr);
        if (instance && vkDestroyInstance) vkDestroyInstance(instance,nullptr);
        if (loader) dlclose(loader);
        if (window) ANativeWindow_release(window);
    }
};
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_seedlessds_app_emu_VulkanNative_create(JNIEnv *env,jobject,jobject surface,jint w,jint h,jint scale,jint mode,jboolean external,jboolean lowLatency) {
    Presenter *presenter=nullptr;
    ANativeWindow *window=ANativeWindow_fromSurface(env,surface);
    if (!window) return 0;
    try {
        presenter=new Presenter();
        presenter->init(window,(unsigned)w,(unsigned)h,(unsigned)scale,seedless_vk::linear_filter(mode),lowLatency!=JNI_FALSE);
        presenter->setRole(external!=JNI_FALSE,(int)mode);
        return (jlong)(uintptr_t)presenter;
    } catch (const std::exception &e) {
        __android_log_print(ANDROID_LOG_WARN,"SeedlessVulkan","Initialization failed: %s",e.what());
        if (presenter) delete presenter; else ANativeWindow_release(window);
        return 0;
    }
}
extern "C" JNIEXPORT jint JNICALL
Java_com_seedlessds_app_emu_VulkanNative_draw(JNIEnv *env,jobject,jlong handle,jfloatArray geometry,jboolean bottomFirst) {
    if (!handle || !geometry || env->GetArrayLength(geometry)!=10) return -1;
    float rects[10]; env->GetFloatArrayRegion(geometry,0,10,rects);
    if (env->ExceptionCheck()) return -1;
    for (float v:rects) if (!std::isfinite(v)) return -1;
    try { return ((Presenter *)(uintptr_t)handle)->draw(rects,bottomFirst!=JNI_FALSE); }
    catch (const std::exception &e) {
        __android_log_print(ANDROID_LOG_WARN,"SeedlessVulkan","Presentation failed: %s",e.what()); return -1;
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_seedlessds_app_emu_VulkanNative_destroy(JNIEnv *,jobject,jlong handle) {
    delete (Presenter *)(uintptr_t)handle;
}

extern "C" JNIEXPORT void JNICALL
Java_com_seedlessds_app_emu_VulkanNative_awaitFrame(JNIEnv *,jobject,jlong handle) {
    if (handle) ((Presenter *)(uintptr_t)handle)->awaitFrame();
}
extern "C" JNIEXPORT void JNICALL
Java_com_seedlessds_app_emu_VulkanNative_wake(JNIEnv *,jobject) { nds_video_wake(); }
