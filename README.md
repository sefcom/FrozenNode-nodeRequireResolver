You can read README_org.md to learn more about the Closure Compiler.

This is a modified closure compiler, that takes in a Node.js file and resolves all of the require statements.
This is intended to be a preprocessor for static analysis.
This is probably not as efficient as it could be, but it works (not counting todos).
This is made to work with the setting "--formatting PRETTY_PRINT".
You should also use "--language_out ECMASCRIPT_2015" or up and "--compilation_level WHITESPACE_ONLY"

preprocessWrapper.py is the entry point for FrozenNode.
You have to manually edit the following global variables if you want to change them:
  java (JAVA), language out (lang_out)
Most of the other settings are configurable or have not been tested with my code.

Currently you may get "TypeError: Cannot read property 'exports' of undefined" for a global variable.
This appears to happen when the require that was resolved is inside a function that was not called.
You can just swap it with the code+function wrapper it is associated with.

CompilerOptions have been added to help (They do not print properly, so I am putting them here. May not fix that)
  1.  --require_resolve_log_location
       This one allows the user to specify a location for a log file. This is needed because of how I have the recursive
       call implemented.
  2.  --reset_rrl
       This tells the program to clear the log.
  3.  --DFS_tracking_log_location
       This tells the program where to keep a log of the found files. Each line is associated with a variable name,
       so that it does not repeat variables and can use them in a similar way to how it is done during runtime.
  4.  --nodejs_source
       This tells the location where you have the source code for NodeJS. This is needed for most complicated projects.
  5.  --node_exe_path
       This is included in case you want to use a different node than the environment variables are set up for. (Or if
       you don't have one)
  6.  --rr_config
       This is an unimplemented option to keep these options in a config folder to shorten the command line arguments.
  Planned Options (If you are using this version you need to change these manually in the code):
  1.  Use java variable


Known TODO:
1. Handle require('require-all'). While doing research on require loops I learned about this.
   It did not affect the files I am working with, so I probably will not fix these.
Changes that would be nice:
1. Allow settings to be set by a config file
2. Little more dynamic (java is the main thing now)


Currently (FOR THESIS RUNNING PROCESS) probably will make bash script or something.
For each project's main node code
  java -jar closure-compiler.jar --module_resolution NODE --js <original_file_absLoc> --js_output_file <output_absLoc> --formatting PRETTY_PRINT --compilation_level WHITESPACE_ONLY --language_out ECMASCRIPT_2015 --require_resolve_log_location <log_absLoc> --reset_rrl true
  python varPrepender.py <output_absLoc>
     OR
  python preprocessorWrapper.py -f <file> -od <output_directory>
     OR
  python preprocessorWrapper.py -mf <multi_file> [-mod <multi_directory>]
     OR
  python preprocessorWrapper.py -mf <multi_file> [-od <output_directory>]

Again there is still a lot that could be fixed.
  Currently should be able to run as long as
    Java environment variable (JAVA_HOME, I think) is set up correctly

    I think every thing else can be configured now. I would like to let this be a config file, but it is not right now.


========= OLD ========
I am preparing to update my code to . I am updating mannually the files I work with, then
I will merge the new closure compiler taking every thing it offers, then pull back the modified.

I have tried marking parts I have added with a comment "James". This is more so I can ctrl+f for it. I should have kept an active list before this, but I will try now.
Because of the weird way I did this I think it will not be preserved correctly.
  1.   CodeGenerator.java
  2.   CompilerOptions.java
  3.   CommandLineRunner.java
  I don't Think I modified these, but they were important places for break points if you want to debug
  1.   Compiler.java
  2.   CodePrinter.java

I added a python script named "preprocessorWrapper.py". This was originally going to help with a resolving issue, but
it did not work. However it does still work as a (mass) caller for the entire preprocessing process.

Currently (FOR THESIS RUNNING PROCESS) probably will make bash script or something.
For each project (run through bable)
For each project's main node code
  java -jar closure-compiler.jar --module_resolution NODE --js <original_file_absLoc> --js_output_file <output_absLoc> --formatting PRETTY_PRINT --require_resolve_log_location <log_absLoc> --reset_rrl true
  python varPrepender.py <output_absLoc>

