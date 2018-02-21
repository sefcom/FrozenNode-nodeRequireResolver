You can read README_org.md to learn more about the closure compiler.

This is a modified closure compiler, that takes in a NodeJS file and resolves all of the require statements.
This is intended to be a preprocessor for static analysis. (I am using it for my thesis).
This is probably not as efficient as it could be, but it works (not counting todos).
This is made to work with the setting "--formatting PRETTY_PRINT".
(I was manually backing up before this)


I have tried marking parts I have added with a comment "James". This is more so I can ctrl+f for it. I should have kept an active list before this, but I will try now.
Because of the weird way I did this I think it will not be preserved correctly.
  1.   CodeGenerator.java
  2.   CompilerOptions.java
  3.   CommandLineRunner.java
  I don't Think I modified these, but they were important places for break points if you want to debug
  1.   Compiler.java
  2.   CodePrinter.java

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
  2.  Use jar location variable


Known TODO:
1. Handle require('require-all'). While doing research on require loops I learned about this.
2. Closure Compiler has trouble with certain files. Can't find exact message but basically it says
   "Can not convert from ES6". I plan to use "babel" on files that it does this with. In fact this update was so I could
   use the babel complied code to see if it fixed anything. (Particularly --nodejs_source)
3. Test that extras are left on with my Global Variables
Changes that would be nice:
1. Adding more options (and useful options)
2. Make prepender apart of the process (made a python file)
3. Little more dynamic


Currently (FOR THESIS RUNNING PROCESS) probably will make bash script or something.
For each project (run through bable)
For each project's main node code
  java -jar closure-compiler.jar --module_resolution NODE --js <original_file_absLoc> --js_output_file <output_absLoc> --formatting PRETTY_PRINT --require_resolve_log_location <log_absLoc> --reset_rrl true
  python varPrepender.py <output_absLoc>


Again there is still a lot that could be fixed.
  Currently should be able to run as long as
    Java environment variable (JAVA_HOME, I think) is set up correctly
    The compiled jar is located at "D:\Sefcom\closure\closure-compiler-myAttempt\target\" with name "closure-compiler-1.0-SNAPSHOT.jar"
    AND you are fine with a "working directory" of "D:\Sefcom\closure\closure-compiler-myAttempt\"

    I think every thing else can be configured now. I would like to let this be a config file, but it is not right now.