# Encrypto

Encrypto is a small, dependency-free command-line tool for encrypting access tokens and other short secrets before storing them in a workspace. It uses password-derived AES-256-GCM authenticated encryption. A random salt and IV are generated for every encryption.

> Encrypto protects the contents of a saved secret, but it is not a full secrets manager. Do not commit decrypted files, passwords, or live credentials to Git.

## Requirements

- JDK 25
- Maven 3.9+ (only needed to build)

On macOS with multiple JDKs, select JDK 25 for the current terminal:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
```

Verify it with `java -version`.

## Package once

From the project directory:

```sh
mvn clean package
```

This runs the tests and creates `target/encrypto.jar`. The JAR contains no third-party runtime dependencies, so you can copy it anywhere that has Java 25.

For a convenient command available in any terminal, add this alias to `~/.zshrc` (adjust the path):

```sh
alias encrypto='java -jar /absolute/path/to/encrypto/target/encrypto.jar'
```

Reload the shell with `source ~/.zshrc`, then run `encrypto`.

## Use it

Interactive mode is the simplest option:

```sh
java -jar target/encrypto.jar
```

Encrypt a prompted token and save it under the ignored `secrets/` directory:

```sh
java -jar target/encrypto.jar encrypt --out secrets/github.secret
```

Decrypt it to the terminal:

```sh
java -jar target/encrypto.jar decrypt --in secrets/github.secret
```

The displayed result is cleared after 12 seconds when running in a real terminal. Redirected output is left intact so shell pipelines continue to work.

Encrypt or decrypt directly to the clipboard without printing the result:

```sh
java -jar target/encrypto.jar encrypt --clipboard
java -jar target/encrypto.jar decrypt --in secrets/github.secret --clipboard
```

Clipboard content is cleared after 12 seconds. If you copy something else first, Encrypto detects the change and preserves the newer clipboard content.

Decrypt it to a file:

```sh
java -jar target/encrypto.jar decrypt --in secrets/github.secret --out token.decrypted
```

Run `java -jar target/encrypto.jar --help` for all options. Passwords are prompted for and are never accepted as command-line arguments, which keeps them out of shell history and process listings. Prefer the prompt or `--in` over `--text` for live credentials because command arguments may be recorded in shell history. Output files are written atomically with owner-only permissions on POSIX file systems.

## Compatibility and format

New values start with `encrypto:v1:` so future formats can be introduced safely. The app can also decrypt Base64 payloads created by the original unversioned implementation.

## Development

```sh
mvn test
mvn package
```

The code is separated into three concerns:

- `Main` owns process startup and exit codes.
- `EncryptoCli` handles terminal arguments and files.
- `SecureCrypto` contains only cryptographic operations.
