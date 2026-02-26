# Agent instructions

## Code style

- **Always use braces for control flow.** Do not write single-statement `if`/`else`/`for`/`while` on one line without braces.

  **Don't:**
  ```java
  if (elem instanceof String s) out.add(s);
  ```

  **Do:**
  ```java
  if (elem instanceof String s) {
      out.add(s);
  }
  ```
