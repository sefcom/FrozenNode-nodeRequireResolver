To test discovery order I needed to have each type of file with the same name.
simpleTest.js is a JavaScript file that returns the string msg. msg's value = "simpleTest.js was loaded" when loaded.
simpleTest.json and copies are JavaScript Object Notation files that return a string msg. msg's value = "simpleTest.js was loaded" when loaded.
simpleTest.node and copies are Native Node files that return a function msg. When msg is called it return "simpleTest.js was loaded".

To make simpleTest.node I needed to write an compile c++ for a node add on.
That is where simpleTest.cc and binding.gyp come in.
simpleTest.cc is the source code for my native addon.
binding.gyp is a file that tells node-gyp how to build the c file. (Basically acts like a make file)



How to build/rebuild the Native Node Files
Step 0: Make sure the following is installed:
    1. node-gyp*
    2. python (2.7)
Step 1: Write c++ file
Step 2: Write binding.gyp
Step 3: Open a terminal in the directory (">" siginfies that the command is input into the terminal)
Step 4: > node-gyp configure
Step 5: > node-gyp build
*If you do not have node-gyp
    1. npm install -g node-gyp