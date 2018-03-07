import sys

# Global Variable
baseVarName = "globalVariable_SHYDNUTN_"
# Variable Functions
def var_name(x):
  name = ""
  if(x >= 0 and x < 10):
    name = baseVarName+"00"+str(x)
  elif(x >= 10 and x < 100):
    name = baseVarName+"0"+str(x)
  elif(x >= 100):
    name = baseVarName+str(x)
  return name
def getNumberOfVars(dfs_loc):
  f = open(dfs_loc,'r')
  t = int(f.readline())
  f.close()
  return t
def appendVarNames(x):
  line0=""
  if(x!=0): line0 = "var "
  for i in range(x-1):
    line0 += var_name(i)+","
  if(x!=0): line0 += var_name(x-1)+";\n"
  return line0
# Require Function
def appendRequires(dfs_loc):
  requires = "";
  f = open(dfs_loc,'r')
  t = f.readline();t = f.readline()
  while(t!=""):
    requires = requires+"require(\""+t.replace("\n","")+"\");\n"
    t = f.readline()
  return requires

# PREPENDER
def filePrepender(file_to_append,dfs_loc,append):
  line0="";reqs=""
  # Open File
  f = open(file_to_append,"r+")
  # Append Correct Number of Global Variables
  line0 = appendVarNames(getNumberOfVars(dfs_loc))
  # If we want to append requires, append requires
  if(append.lower()=="true" or append.lower()=="t"):
    reqs = appendRequires(dfs_loc)
  # Add the variables and requires to the front
  stuffToAdd = line0+reqs
  current = f.read()
  f.seek(0,0)
  f.write(stuffToAdd+current)
  f.close()

# This should be made more dynamic, but I don't plan to fix it.
fta = "";dl = "../DFSMapping.txt";a = "false"
def getGlobalVars():
  global fta,dl,a
  length = len(sys.argv)
  if(length == 1):
    print("This file needs you to specify the file to append.\n\t python <thisScript> <file_name> [<dfs_log_name> [<append_requires?>]]")
  fta = sys.argv[1]    # This is the location+name of the file to append
  if(length > 2): dl = sys.argv[2]     # This is the location+name of the dfs_log
  if(length > 3): a = sys.argv[3]      # This is whether or not we want to append requires("<file>"); to the front of the file.
                       #    Do this if you are planning to run through the modified closure-compiler again
if __name__ == "__main__":
  getGlobalVars()
  filePrepender(fta,dl,a)