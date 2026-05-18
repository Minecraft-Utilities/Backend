# Spring Boot 4 & Java 25 Backend Standards

## General Rules
- Always use targeted code blocks. Do not rewrite an entire class if only a method changes.
- Never edit `pom.xml` or add Maven dependencies without asking for explicit permission first.

## Tech Stack Constraints (CRITICAL)
- **Framework:** Spring Boot 4.x (utilizing Spring Framework 7 and Jakarta EE 11 standards).
- **Java Version:** Java 25 LTS. Take full advantage of modern features (e.g., Records, Switch Pattern Matching, Sequenced Collections, and Virtual Threads).
- **Packages:** Use modern `jakarta.validation` and `jakarta.persistence` namespaces. Absolutely NO legacy `javax` packages.

## Strict Java Code Style
- **Control Flow Braces:** ALWAYS use explicit braces `{}` for control flow. Do not write single-statement `if`, `else`, `for`, `while`, or `do-while` loops on a single line without braces.
  - *DON'T:* `if (elem instanceof String s) out.add(s);`
  - *DO:* `if (elem instanceof String s) { out.add(s); }`

## Architecture & Data Handling
- **Layered Pattern:** Maintain strict isolation: Controller -> Service -> Repository -> Entity.
- **Data Transfer:** Never expose database Entities directly to the controller layer. Always map database objects to explicit Data Transfer Objects (DTOs) or Java Records.
- **Validation:** Enforce standard `jakarta.validation` annotations (e.g., `@NotNull`, `@NotBlank`) on all inbound controller DTOs/Records.

## Compilation Guardrail (Token Burn Prevention)
- If a Maven compilation or test suite build fails, STOP immediately. Report the exact console error log to the user. Do not try to guess or hallucinate fixes in a repetitive loop.
