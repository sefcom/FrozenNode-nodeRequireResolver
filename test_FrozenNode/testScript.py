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
  if(err!="" and debug > 0):
    print("\n\nERROR:\n")
    print(err)
  return out
def callCommandWithShell(command,wd):
  com = subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE,cwd=wd,shell=True)
  out, err = com.communicate()
  if(err!="" and debug > 0):
    print("\n\nERROR:\n")
    print(err)
  return out

# FUNCTIONS NEEDED TO COMPILE THE REQUIRE RESOLVER
#      Should not matter too much, but I use:
#           Maven 3: (3.3.9)
#           Java:    1.8.0_51
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
  print names
  
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
  print names
  
  # Call twice and get output
  command = ["node",fn]
  orgOut = callCommand(command,wd)
  shutil.copyfile(ofn,fn)
  newOut = callCommand(command,wd)
  shutil.copyfile(bfn,fn)
  os.remove(bfn)
  return orgOut, newOut

# Test tool to make sure it works
def compare(o,n,file):
  print "\n\n\n",o
  print "\n\n\n",n
  oLineSep = o.split("\n")
  nLineSep = n.split("\n")
  exit = 0
  if len(nLineSep) != len(oLineSep):
    print "ERROR: Output lengths were not the same for",file
    exit = 1
  for i in range(len(nLineSep)):
    if oLineSep[i] != nLineSep[i]:
      print "ERROR: line",i,"in",file,"is not the same"
      print "\torg",i,"in",file,"\n\t\t",oLineSep[i]
      print "\tnew",i,"in",file,"\n\t\t",nLineSep[i]
      exit = 2
  return exit
def runTest(file):
  oo, no = callSingleFile(file,"")
  e = compare(oo,no,file)
  if e != 0: return e
  else: return "PASSED:\t\t "+file+" test"
def runTest2(file):
  oo, no = callSingleFileInPlace(file,"")
  e = compare(oo,no,file)
  if e != 0: return e
  else: return "PASSED:\t\t "+file+" test"
def rt(file,m,type):
  if type == 1:
    l = runTest(file)
  elif type == 2:
    l = runTest2(file)
  try:
    if l.__contains__("PASSED"): m.append(l)
  except AttributeError:
    if l != 0:
      printM(m)
      print "FAILED:\t\t",file,"test"
      sys.exit(l)
def printM(m):
  print callCommandWithShell(["ls"],getAbsFilename("./test_FrozenNode/ppw/"))
  print "\n\n"
  for mes in m:
    print mes
def runTests():
  m = []
  rt("./test_FrozenNode/testJS.js",m,1)
  rt("./test_FrozenNode/testJS2.js",m,1)
  rt("./test_FrozenNode/main.js",m,1)					# If it passes up to here, then 1 require deep requires are passing
  rt("./test_FrozenNode/multilayerStart.js",m,1)		# If it passes this one, it can require more than one layer deep
  rt("./test_FrozenNode/loopVarS.js",m,1)				# If it passes this one, it can resolve require loops (CACHING WORKS TOO)
  rt("./test_FrozenNode/testJSON.js",m,1)
  rt("./test_FrozenNode/testJSON2.js",m,1)				# If it passes these two it can handle JSON
  rt("./test_FrozenNode/testFileTypes_NoNode.js",m,1)	# Makes sure caching works and you can require both supported files
  rt("./test_FrozenNode/testDirTypes_ne.js",m,2)		# Confirms it does not break the file when .node type is required
  printM(m)
  print "PASSED ALL TEST CASES"
  
# CODE TO TEST FAILING CODE
def checkLog(file,dn,type):
  found = 0
  temp = ""
  fn = file
  if file[0:1] != "/" or file[1:2] != ":": fn = getAbsFilename(file)
  names = pw.fileNameAssigner(fn,dn)
  log = names["l1"]
  f = open(log,"r").read()
  if type == 1 or type == 2:
    if f.__contains__("A path may need to be provided"):
      temp = "PASSED:\t\t "+file+" log check"
      found = 1
  elif type == 3 or type == 4:
    if f.__contains__(".node is a valid file type"):
      temp = "PASSED:\t\t "+file+" log check"
      found = 1
  if found == 0:
	temp = "FAILED:\t\t "+file+" log check"
  return temp
def failTests(file,m,type):
  temp = "";
  pw.node_source_resolve = "true"
  if type%2 == 0:
	oo, no = callSingleFileInPlace(file,"")
  else:
    oo, no = callSingleFile(file,"")
  pw.node_source_resolve = "false"
  e = compare(oo,no,file)
  if e ==0: temp = "PASSED:\t\t "+file+" test"
  l = checkLog(file,"",type)
  m.append(temp)
  m.append(l)
  if l.__contains__("PASSED") and e==0:
    return 0
  printM(m)
  sys.exit(1)
def specialTests():
  m = []
  failTests("./test_FrozenNode/reqMissing.js",m,1)
  failTests("./test_FrozenNode/testNode.js",m,4)
  printM(m)
  print "PASSED ALL SPECIAL TESTS"


if __name__ == "__main__":
  # Compile Require Resolver Jar
  #compileJARWrapper()
  
  # Set up globals
  ccjar="";nsrc="";nsr="";java="";lang=""
  ccjar = os.path.abspath(os.path.join(dirPW,"./target/closure-compiler-1.0-SNAPSHOT.jar")).replace("//","/")
  setPWGlobals(ccjar,nsrc,nsr,java,lang)
  
  # Call tool
  #oo, no = callSingleFileInPlace("./test_FrozenNode/testDirTypes.js","") # In place because of require.resolve and require .node files
  #compare(oo,no,"./test_FrozenNode/testDirTypes.js")
  #oo, no = callSingleFile("./test_FrozenNode/testJSON.js","")
  # Both of the above work for me... however testDirTypes throws errors (purposefully) which have different line numbers
  # Now all that needs to be done is to actually make the individual modular test cases and compare output
  runTests()
  print "\n\n"
  specialTests()