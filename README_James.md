You can read the README.md to learn more about the closure compiler.

This is a modified closure compiler, that takes in a NodeJS file and resolves all of the require statements.
This is intended to be a preprocessor for static analysis. (I am using it for my thesis).
This is probably not as efficient as it could be, but it works (not counting todos).
This is made to work with the setting "--formatting PRETTY_PRINT".
(I was manually backing up before this)


I have tried marking parts I have added with a comment "James". This is more so I can ctrl+f for it. I should have kept an active list before this, but I will try now.
Beacuse of the weird way I did this I think it will not be preserved correctly.
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
  Planned Options (If you are using this version you need to change these manually in the code):
  1.  An option for location of native modules
  2.  Use java variable
  3.  Use jar location variable


Known TODO:
1. Handle require loops. I am uploading this to GitHub BEFORE I start this so I have a backup.
   I do, however, have several ideas on how to handle this.
2. Handle require('require-all'). While doing research on require loops I learned about this.
3. Closure Compiler has trouble with certain files. Can't find exact message but basically it says
   "Can not convert from ES6". I plan to use "bable" on files that it does this with.