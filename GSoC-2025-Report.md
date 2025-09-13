# Google Summer of Code 2025 – Final Project Report  

## Project Goals  
The goal of this project was to **remove the Checker Framework’s dependency on JavaParser** and instead use the **javac parser**.  

JavaParser can be buggy, and may not provide optimal support for the latest Java versions. This may limit Checker Framework users.  

The long-term effort has three parts:  
1. Replace JavaParser for `.ajava` files.  
2. Replace JavaParser for stub files.  
3. Replace JavaParser for parsing Java expressions in annotations, and possibly remove the custom `JavaExpression` class.  

My work focused on part (3): updating how the Checker Framework parses Java expressions.  

---

## What I Did  
- Looked at the existing visitor inside `JavaExpressionParseUtil.java` called `ExpressionToJavaExpressionVisitor`, which converted JavaParser `Expression` nodes into the Checker Framework’s `JavaExpression`.  
- Wrote a new visitor called `ExpressionTreeToJavaExpressionVisitor` that does the same job, but starting from `javac`’s `ExpressionTree` nodes instead of JavaParser.  
- Updated the parsing logic so it now uses `javac`’s AST instead of JavaParser.  
- Removed the dependency on JavaParser for this module.  
- Ran the test suite to make sure everything still worked correctly.  

---

## Current State  
- The Checker Framework no longer uses JavaParser for parsing Java expressions in annotations.  
- A new visitor (`ExpressionTreeToJavaExpressionVisitor`) is in place, based only on `javac`.  
- All tests pass, so the functionality is the same as before.  

---

## Code Upstream Status  
The commit that adds `ExpressionTreeToJavaExpressionVisitor` and removes JavaParser from this part of the code was **merged upstream** into the Checker Framework repository.  

🔗 [Commit: Remove JavaParser dependency in expression parsing](https://github.com/typetools/checker-framework/commit/db5e35ccf83c94c715c56b647abd903e6512587b)  

---

## Challenges and Lessons Learned  
- The biggest challenge was trying (and initially failing) to parse an expression string into its required `ExpressionTree` object. Doing this manually caused memory issues that were hard to debug. With the help of my mentor, I learned there was a library approach that handled these issues.  
- Converting from JavaParser’s AST to `javac`’s AST required learning the differences between the two APIs.  
- The test suite was very helpful to confirm that the new code worked the same as the old code.  

---
