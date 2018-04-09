import sys,os,varPrepender
from shutil import copyfile
import subprocess
# TODO IMPORTANT: This is a vulnerable program. It needs to be fixed, however I will not be doing that for my thesis. 
#                 If you want to use this you have been warned.

# Global Variables
CCJAR = "D:/Sefcom/closure/closure-compiler-myAttempt/target/closure-compiler-1.0-SNAPSHOT.jar"
NSRC = "D:/Sefcom/NodeJS_code/node-v6.13.0"
JAVA = "java"
lang_out = "ECMASCRIPT_2015"
node_source_resolve = "false"

# THIS IS THE FILE NAME ASSIGNER. IT HELPS WITH KEEPING THE NAMES THE SAME
def fileNamesAssigner(fileNames,dirName,d2):
  # Fill the dictionary with file names. fnCopy needs to be in original directory so that require.resolve("") works correctly.
  file_name = fileNames["fn"]
  fileNames["org_dir"] = d2
  fileNames["out_dir"] = dirName
  fileNames["fnClean"] = d2+fileNames["fn"]+".js"
  fileNames["fnCopy"] = d2+file_name+"_1.ppw.js"
  fileNames["dl1"] = dirName+file_name+"_1.dfs.txt"
  fileNames["dl2"] = dirName+file_name+"_2.dfs.txt"
  fileNames["l1"] = dirName+file_name+"_1.log.txt"
  fileNames["l2"] = dirName+file_name+"_2.log.txt"
  fileNames["of"] = dirName+file_name+"-out.compiled.js"
def fileNameAssigner(fn,dn):
  if(dn == ""): print("Assuming you want to use the original directory. Please Stop and specify the directory name(s)")
  cleanedfn = fn.replace("\\\\","\\").replace("\\","/")
  # Create a dictionary of file names
  fileNames = {"org_dir":"out_dir","":"","fn":"","fnClean":"","fnCopy":"","dl1":"","dl2":"","l1":"","l2":"","of":""}
  file_name = cleanedfn.split("/")[-1]
  file_name = file_name.split(".js")[0]
  fileNames["fn"] = file_name
  # Get Current Dir
  parts = cleanedfn.split("/")
  d2 = ""
  if os.name == "posix": d2 ="/"
  for i in range(len(parts)-1):
    if(parts[i]!=""):
      if(i==0): d2=parts[i]+"/"
      else: d2 = d2+parts[i]+"/"
  # If directory out is unspecified make it equal to current directory + ppw
  if(dn == ""):
    dn = d2+"ppw/"
  else:
    parts = dn.replace("\\\\","\\").replace("\\","/").split("/")
    for i in range(len(parts)):
      if(parts[i]!=""):
        if(i==0): dn=parts[i]+"/"
        else: dn = dn+parts[i]+"/"
  # Assign file names (absolute location)
  fileNamesAssigner(fileNames,dn,d2)
  return fileNames

# Create Directory
def createDir(dn):
  if not os.path.exists(dn):
    os.makedirs(dn)

# Closure Compiler calls
def closureCall_1(fileNames):
  #command = ["java -jar "+CCJAR+\
  #          " --module_resolution NODE --compilation_level WHITESPACE_ONLY --formatting PRETTY_PRINT --language_out ECMASCRIPT_2015"+\
  #          " --js "+fileNames["fnClean"]+" --reset_rrl true --require_resolve_log_location "+fileNames["l1"]+\
  #          " --DFS_tracking_log_location "+fileNames["dl1"]+" --nodejs_source "+NSRC
  command = [JAVA,"-jar",CCJAR,"--module_resolution","NODE","--compilation_level","WHITESPACE_ONLY","--formatting",
      "PRETTY_PRINT","--language_out",lang_out,"--js",fileNames["fnClean"],"--reset_rrl","true",
      "--require_resolve_log_location",fileNames["l1"],"--DFS_tracking_log_location",fileNames["dl1"],"--nodejs_source",NSRC,
	  "--resolve_NSC",node_source_resolve]
  return command
def closureCall_2(fileNames):
  #command = "java -jar "+CCJAR+\
  #          " --module_resolution NODE --compilation_level WHITESPACE_ONLY --formatting PRETTY_PRINT --language_out ECMASCRIPT_2015"+\
  #          " --js "+fileNames["fnCopy"]+" --js_output_file "+fileNames["of"]+" --reset_rrl true"+\
  #          " --require_resolve_log_location "+fileNames["l2"]+" --DFS_tracking_log_location "+fileNames["dl2"]+" --nodejs_source "+NSRC
  command = [JAVA,"-jar",CCJAR,"--module_resolution","NODE","--compilation_level","WHITESPACE_ONLY","--formatting",
      "PRETTY_PRINT","--language_out",lang_out,"--js",fileNames["fnCopy"],"--js_output_file",fileNames["of"],"--reset_rrl","true",
      "--require_resolve_log_location",fileNames["l2"],"--DFS_tracking_log_location",fileNames["dl2"],"--nodejs_source",NSRC,
	  "--resolve_NSC",node_source_resolve]
  return command
def closureCall(fileNames):
  command = [JAVA,"-jar",CCJAR,"--module_resolution","NODE","--compilation_level","WHITESPACE_ONLY","--formatting",
      "PRETTY_PRINT","--language_out",lang_out,"--js",fileNames["fnClean"],"--js_output_file",fileNames["of"],"--reset_rrl","true",
      "--require_resolve_log_location",fileNames["l1"],"--DFS_tracking_log_location",fileNames["dl1"],"--nodejs_source",NSRC,
	  "--resolve_NSC",node_source_resolve]
  return command
def commandCall(command):
  com = subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  out, err = com.communicate()
  if(err!=""): print(err)

# This is the actual wrapper. It calls the commands in order.
def callCommands(file_name,dir_name):
  print("[ INFO ] WORKING ON \""+file_name+"\"")
  try:
    fileNames = fileNameAssigner(file_name,dir_name)
    createDir(fileNames["out_dir"])
    print("[ INFO ] CLOSURE CALL \""+file_name+"\"")
    commandCall( closureCall(fileNames) )
    varPrepender.filePrepender(fileNames["of"],fileNames["dl1"],"false")
  except Exception, e:
    print("There was an exception in CallCommand for file: "+file_name)
    print(e)
def original(file_name,dir_name):
  # I did not think about how it did a DFS for the required files. This does not work. :(
  # Of course forcing it to resolve will not help either. :/
  # FOR NOW I WILL RESOLVE BY HAND
  print("[ INFO ] WORKING ON \""+file_name+"\"")
  try:
    #planning()
    fileNames = fileNameAssigner(file_name,dir_name)
    createDir(fileNames["out_dir"])
    print("[ INFO ] FIRST CLOSURE CALL \""+file_name+"\"")
    commandCall( closureCall_1(fileNames) )
    copyfile(fileNames["fnClean"],fileNames["fnCopy"])
    varPrepender.filePrepender(fileNames["fnCopy"],fileNames["dl1"],"true")
    print("[ INFO ] SECOND CLOSURE CALL \""+file_name+"\"")
    commandCall( closureCall_2(fileNames) )
  except Exception, e:
    print("There was an exception in original for file: "+file_name)
    print(e)

# This allows you to specify multi-files to run through and call for each file in it.
def readFileAndCall(multifile,multidir,outdir):
  f = open(multifile,'r')
  t = f.readline()
  if(multidir):
    f2 = open(multidir,'r')
    t2 = f2.readline()
    while(t!=""):
      callCommands(t.replace("\n",""),t2.replace("\n",""))
      t = f.readline()
      t2 = f2.readline()
    f2.close()
  else:
    print("Assuming you want to use output directory")
    while(t!=""):
	  callCommands(t.replace("\n",""),outdir)
	  t = f.readline()
  f.close()

# This reads and keeps the command line arguments
# clOpt are kept to only the below functions, so the code could be called from other python scripts.
clOpt = {"mf":"","mod":"","file_name":"","od":"","nsr":node_source_resolve}
clOptFound = 0
def checkArgs(a0,a1):
  global clOpt, clOptFound
  a0L = a0.lower()
  if(a0L == "--multifile" or a0L=="-mf"):
    if(a1 == ""): print("ERROR: No multi-file was specified after the multifile option")
    clOpt["mf"] = a1
    clOptFound = 1
  elif(a0L == "--multioutput_directory" or a0L == "--multioutdir" or a0L == "-mod"):
    if(a1 == ""): print("ERROR: No file was specified after the multi-output directory option")
    clOpt["mod"]=a1
    clOptFound = 1
  elif(a0L == "--file" or a0L=="--js" or a0L=="-f"):
    if(a1 == ""): print("ERROR: No file was specified after the file option")
    clOpt["file_name"]=a1
    clOptFound = 1
  elif(a0L == "--output_directory" or a0L == "--outdir" or a0L == "-od"):
    if(a1 == ""): print("ERROR: No file was specified after the output directory option")
    clOpt["od"]=a1
    clOptFound = 1
  elif(a0L == "--node_source_resolve" or a0L == "-nsr"):
    if(a1 == ""): print("ERROR: No file was specified after the node source resolve option")
    clOpt["nsr"]=a1
    clOptFound = 1
  elif(a0L == "--node_source_location" or a0L == "--node_source" or a0L == "-nsrc"):
    global NSRC
    if(a1 == ""): print("ERROR: No file was specified after the node source location option")
    else:NSRC=a1
    clOptFound = 1
  elif(a0L == "--closure_compiler_jar" or a0L == "--require_resolver_jar" or a0L == "-ccjar" or a0L == "-rrjar"):
    global CCJAR
    if(a1 == ""): print("ERROR: No file was specified after the require resolver jar option")
    else:CCJAR=a1
    clOptFound = 1
  elif(a0L == "--help" or a0L=="-h"):
    print("  --multifile, -mf:\n\t This tells the program to read the file with the location specified.\n\t" +\
	          " Each line in the file should be a file you want to run through the entire pre-process process.\n" +\
		  "  --multioutput_directory, --multioutdir, -mod:\n\t This tells the program the desired output directory.\n\t" +\
	          " Each line in the file should correspond to a line in your multi-file.\n" +\
		  "  --file, --js, -f:\n\t This tells the program that you want to pre-process the specified file.\n" +\
		  "  --output_directory, --outdir, -od:\n\t This tells the program the output directory to put all files created by the preprocessor.\n" +\
		  "  --node_source_resolve, -nsr:\n\t This tells the program to resolve internal modules [True or False] (modules compiled into Node.js)\n" +\
		  "  --node_source_location, --node_source, -nsrc:\n\t This tells the program where to find the source code for internal modules (modules compiled into Node.js)\n\t" +\
	          " I recommend changing the hardcoded default at the top of this file for this one.\n" +\
		  "  --closure_compiler_jar, --require_resolver_jar, -ccjar, -rrjar:\n\t This tells the program where to find the Require Resolver JAR.\n\t" +\
	          " I recommend changing the hardcoded default at the top of this file for this one.\n" +\
		  "  --help, -h:\n\t This causes this message to print.\n")
    clOptFound = 1
def readCLoptions():
  args = sys.argv
  for i in range(1,len(args)-1):
    checkArgs(args[i],args[i+1])
  checkArgs(args[len(args)-1],"")
  if(clOptFound == 0):
    print("\nIf this is run from the command line, options must be specified.")
    checkArgs("-h","")
    exit(1)

if __name__ == "__main__":
  readCLoptions()
  node_source_resolve = clOpt["nsr"]
  if(clOpt["file_name"] != ""):
    callCommands(clOpt["file_name"].replace("\n",""),clOpt["od"].replace("\n",""))
  if(clOpt["mf"] != ""):
    readFileAndCall(clOpt["mf"],clOpt["mod"],clOpt["od"].replace("\n",""))