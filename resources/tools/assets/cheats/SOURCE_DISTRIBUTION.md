# 金手指对应源文件

本发布目录将原始 cheats.xml 无损压缩为 cheats.xml.gz，以减小 Git 仓库体积。AGPL 许可、原作者声明与其他源文件均保留。Android 构建使用现成 usrcheat.dat，不需要解压 XML；修改或使用 XML 时可在此目录执行：

```sh
python -c "import gzip,pathlib; p=pathlib.Path('cheats.xml.gz'); p.with_suffix('').write_bytes(gzip.decompress(p.read_bytes()))"
```

解压后的 SHA-256：`8e28cd24dc7a282a86bd080931005e16de24508749e56ec2c495a2947fe2aef4`
