Google Summer of Code 2025 – Final Project Report
Project Goals
The goal of this project was to remove the Checker Framework’s dependency on JavaParser and instead use the javac parser.
JavaParser can be buggy, and may not provide optimal support for the latest Java versions. This may limit Checker Framework users.
The long-term effort has three parts:
Replace JavaParser for .ajava files.


Replace JavaParser for stub files.


Replace JavaParser for parsing Java expressions in annotations, and possibly remove the custom JavaExpression class.


My work focused on part (3): updating how the Checker Framework parses Java expressions.

What I Did
I looked at the existing visitor inside JavaExpressionParseUtil.java called ExpressionToJavaExpressionVisitor, which converted JavaParser Expression nodes into the Checker Framework’s JavaExpression.


Wrote a new visitor called ExpressionTreeToJavaExpressionVisitor that does the same job, but starting from javac’s ExpressionTree nodes instead of JavaParser.


Updated the parsing logic so it now uses javac’s AST instead of JavaParser.


Removed the dependency on JavaParser for this module.


Ran the test suite to make sure everything still worked correctly.



Current State
The Checker Framework no longer uses JavaParser for parsing Java expressions in annotations.


A new visitor (ExpressionTreeToJavaExpressionVisitor) is in place, based only on javac.


All tests pass, so the functionality is the same as before.




Code Upstream Status
The commit that adds ExpressionTreeToJavaExpressionVisitor and removes JavaParser from this part of the code was merged upstream into the Checker Framework repository.



Challenges and Lessons Learned

The biggest challenge was trying and failing on appropriately parsing an expression string into its required ExpressionTree object. Trying to do it manually presented some memory issues which were not very easy to understand or debug. With the help of my mentor I was able to learn that there was a way to use a library which handles all these issues.
‘Converting from JavaParser’s AST to javac’s AST required learning the differences between the two APIs.


The test suite was very helpful to confirm that the new code worked the same as the old code.




