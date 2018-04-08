import sys, os, subprocess, shutil

debug = 0;

# Import preprocessorWrapper (semi-dynamically)
dirPW = os.path.abspath(os.path.join(os.path.dirname(os.path.realpath(__file__)),"../"))
sys.path.append(dirPW)
import preprocessorWrapper as pw



# Call commands
def callCommand(command,wd):
  com = subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE,cwd=wd)
  out, err = com.communicate()
  if(err!=""):
    print("/n/nERROR:/n")
    print(err)
  return out
def callCommandWithShell(command,wd):
  com = subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE,cwd=wd,shell=True)
  out, err = com.communicate()
  if(err!=""):
    print("/n/nERROR:/n")
    print(err)
  return out

# FUNCTIONS NEEDED TO COMPILE THE REQUIRE RESOLVER
# Should not matter too much, but I use
# Maven 3 (3.3.9)
# Java 1.8.0_51
def compileJar():
  command = ["mvn","-DskipTests","-pl","externs/pom.xml,pom-main.xml,pom-main-shaded.xml","-X"]
  return callCommandWithShell(command,dirPW) # Called with shell because of mvn
def compileJARWrapper():
  # Compile JAR
  temp = compileJar()
  p = temp.split("\n")
  s = 0
  for i in range(len(p)-11,len(p)-1):
    print p[i]
    if p[i].__contains__("[INFO] BUILD SUCCESS"):
	  s = 1
  if s == 0: 
    print "The version does not currently compile"
    sys.exit(1)

# HELPER FUNCTIONS
def getAbsFilename(filename):
  absFile = os.path.abspath(os.path.join(dirPW,filename)).replace("//","/")
  if debug>1: print absFile
  return absFile
  
# These call FrozenNode using the wrapper. (api-ish)
def setPWGlobals(ccjar,nsrc,nsr,java,lang):
  if ccjar != "": 
    pw.CCJAR = ccjar
    if debug>1: print ccjar
  if nsrc != "": 
    pw.NSRC = nsrc
    if debug>1: print nsrc
  if nsr != "": 
    pw.NSR = nsr
    if debug>1: print nsr
  if java != "": 
    pw.JAVA = java
    if debug>1: print pw
  if lang != "":
    pw.lang = lang
    if debug>1: print lang
def pwCall(filename,outputdir):
  fn = filename
  if filename[0:1] != "/" or filename[1:2] != ":": fn = getAbsFilename(filename)
  if debug>2: print fn
  pw.callCommands(fn,outputdir)

# Get the out put of the original and compiled files
def callSingleFile(filename,dn):
  # Get file names and run FrozenNode
  fn = filename
  if filename[0:1] != "/" or filename[1:2] != ":": fn = getAbsFilename(filename)
  names = pw.fileNameAssigner(fn,dn)
  pwCall(fn,dn)
  
  # Set up names of files to run
  ofn = names["of"]
  dir = names["out_dir"]
  wd = names["org_dir"]
  
  # Call twice and get output
  command = ["node",fn]
  command2 = ["node",ofn]
  orgOut = callCommand(command,wd)
  newOut = callCommand(command2,dir)
  return orgOut, newOut
def callSingleFileInPlace(filename,dn):
  # Get file names and run FrozenNode
  fn = filename
  if filename[0:1] != "/" or filename[1:2] != ":": fn = getAbsFilename(filename)
  names = pw.fileNameAssigner(fn,dn)
  pwCall(fn,dn)
  
  # Set up names of files to run
  ofn = names["of"]
  bfn = getAbsFilename(filename.replace(".js",".bak.js"))
  shutil.copyfile(fn,bfn)
  wd = names["org_dir"]
  
  # Call twice and get output
  command = ["node",fn]
  orgOut = callCommand(command,wd)
  shutil.copyfile(ofn,fn)
  newOut = callCommand(command,wd)
  shutil.copyfile(bfn,fn)
  os.remove(bfn)
  return orgOut, newOut

if __name__ == "__main__":
  # Compile Require Resolver Jar
  #compileJARWrapper()
  
  # Set up globals
  ccjar="";nsrc="";nsr="";java="";lang=""
  ccjar = os.path.abspath(os.path.join(dirPW,"./target/closure-compiler-1.0-SNAPSHOT.jar")).replace("//","/")
  setPWGlobals(ccjar,nsrc,nsr,java,lang)
  
  # Call tool
  oo, no = callSingleFileInPlace("./test_FrozenNode/testDirTypes.js","") # In place because of require.resolve and require .node files
  oo, no = callSingleFile("./test_FrozenNode/testJSON.js","")
  # Both of the above work for me... however testDirTypes throws errors (purposefully) which have different line numbers
  # Now all that needs to be done is to actually make the individual modular test cases and compare output
  