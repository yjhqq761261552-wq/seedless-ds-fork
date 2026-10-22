from pathlib import Path
import tempfile,subprocess,re,json
import validate_shaders as v

results=[]
scratch=Path(__file__).parent/'shader-check'
scratch.mkdir(exist_ok=True)
for recipe in sorted(v.SHADERS.glob('*.dfx')):
    text=recipe.read_text(encoding='utf-8',errors='replace')
    include=''
    for match in re.finditer(r'<include>\s*file=([^\s<]+)',text):
        include+=(recipe.parent/match[1]).read_text(encoding='utf-8',errors='replace')
    for index,shaderpass in enumerate(re.findall(r'<pass>(.*?)</pass>',text,re.S)):
        shader=re.search(r'^shader=([^\r\n]+)',shaderpass,re.M)[1].strip()
        ds=(recipe.parent/shader).read_text(encoding='utf-8',errors='replace')
        with tempfile.TemporaryDirectory(dir=scratch) as tmp:
            vert=Path(tmp)/'shader.vert'; frag=Path(tmp)/'shader.frag'
            vert.write_text(v.block(text,'vheader')+v.block(text,'header')+include+v.block(ds,'vertex'),encoding='utf-8')
            frag.write_text(v.block(text,'fheader')+v.block(text,'header')+include+v.block(ds,'fragment'),encoding='utf-8')
            run=subprocess.run([str(v.GLSLANG),'--glsl-version','100','-l',str(vert),str(frag)],capture_output=True,text=True)
            results.append(dict(recipe=recipe.name,shader=shader,passIndex=index,success=run.returncode==0,log=run.stdout+run.stderr))
failed=[x for x in results if not x['success']]
Path(__file__).with_name('shader-link-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Recipes: {len(list(v.SHADERS.glob("*.dfx")))}, pass pairs: {len(results)}, link failures: {len(failed)}')
for fail in failed: print(fail['recipe'],fail['passIndex'],fail['log'])
raise SystemExit(bool(failed))
