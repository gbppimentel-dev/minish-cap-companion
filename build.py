"""Build Minish Cap Guide. Signing is optional and uses external credentials."""
from pathlib import Path
import argparse,os,subprocess,shutil,zipfile
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--keystore',type=Path,default=os.environ.get('MINISH_KEYSTORE'))
a=p.parse_args()
sdk=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Library/Android/sdk')))
bt=sdk/'build-tools/36.0.0';jar=sdk/'platforms/android-36/android.jar'
env=dict(os.environ)
java=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else Path(shutil.which('javac') or '').parent
if os.environ.get('JAVA_HOME'):env['PATH']=str(java)+os.pathsep+env.get('PATH','')
def run(*args):subprocess.run([str(x) for x in args],cwd=R,env=env,check=True)
for name in ['build/classes','build/dex','dist']:(R/name).mkdir(parents=True,exist_ok=True)
run(bt/'aapt','package','-f','-M','AndroidManifest.xml','-S','res','-A','assets','-I',jar,'-F','build/resources.apk')
run(java/'javac','-source','8','-target','8','-bootclasspath',str(jar)+os.pathsep+str(bt/'core-lambda-stubs.jar'),'-d','build/classes',*sorted((R/'src').rglob('*.java')))
run(bt/'d8','--lib',jar,'--min-api','26','--output','build/dex',*sorted((R/'build/classes').rglob('*.class')))
shutil.copy2(R/'build/resources.apk',R/'build/unsigned.apk')
with zipfile.ZipFile(R/'build/unsigned.apk','a') as z:z.write(R/'build/dex/classes.dex','classes.dex')
run(bt/'zipalign','-f','4','build/unsigned.apk','build/aligned.apk')
if a.keystore:
 if not env.get('MINISH_KEYSTORE_PASSWORD'):raise SystemExit('Set MINISH_KEYSTORE_PASSWORD to sign.')
 out=R/'dist/MinishCapGuide-v1.0-build8.apk'
 run(bt/'apksigner','sign','--ks',a.keystore,'--ks-pass','env:MINISH_KEYSTORE_PASSWORD','--out',out,'build/aligned.apk')
 run(bt/'apksigner','verify',out)
else:
 out=R/'dist/MinishCapGuide-v1.0-build8-unsigned.apk';shutil.copy2(R/'build/aligned.apk',out)
print(out)
