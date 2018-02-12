import sys
file_to_append = sys.argv[1]
line0 = "var "
baseVarName = "globalVariable_SHYDNUTN_"
def var_name(x):
  name = ""
  if(x >= 0 and x < 10):
    name = baseVarName+"00"+str(x)
  elif(x >= 10 and x < 100):
    name = baseVarName+"0"+str(x)
  elif(x >= 100):
    name = baseVarName+str(x)
  return name

def appendVarNames(x):
  global line0
  for i in range(x-1):
    line0 += var_name(i)+","
  line0 += var_name(x-1)+";\n"

def getNumberOfVars():
  # TODO modify this if I change it in my modified closure compiler
  f = open("../DFSMapping.txt",'r')
  t = int(f.readline())
  return t

def filePrepender():
  f = open(file_to_append,"r+")
  appendVarNames(getNumberOfVars())
  current = f.read()
  f.seek(0,0)
  f.write(line0+current)
  

filePrepender()