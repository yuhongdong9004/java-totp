# Time-based One Time Password (MFA) Library for Java

[![CircleCI](https://circleci.com/gh/samdjstevens/java-totp/tree/master.svg?style=svg&circle-token=10b865d8ba6091caba7a73a5a2295bd642ab79d5)](https://circleci.com/gh/samdjstevens/java-totp/tree/master) [![Maven Central](https://img.shields.io/maven-central/v/dev.samstevens.totp/totp.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22dev.samstevens.totp%22%20AND%20a:%22totp%22)

A java library to help generate and verify time-based one time passwords for Multi-Factor Authentication.

Generates QR codes that are recognisable by applications like Google Authenticator, and verify the one time passwords they produce.

Inspired by [PHP library for Two Factor Authentication](https://github.com/RobThree/TwoFactorAuth), a similar library for PHP.

## Requirements

- Java 8+



## Installation

#### Maven

To add this library to your java project using Maven, add the following dependency:

```xml
<dependency>
  <groupId>dev.samstevens.totp</groupId>
  <artifactId>totp</artifactId>
  <version>1.5.1</version>
</dependency>
```

#### Gradle

To add the dependency using Gradle, add the following to the build script:

```
dependencies {
  compile 'dev.samstevens.totp:totp:1.5.1'
}
```



## Usage

- [Generating secrets](#generating-a-shared-secret)
- [Generating QR codes](#generating-a-qr-code)
- [Verifying one time passwords](#verifying-one-time-passwords)
- [Using different time providers](#using-different-time-providers)
- [Recovery codes](#recovery-codes)



### Generating a shared secret

To generate a secret, use the `dev.samstevens.totp.secret.DefaultSecretGenerator` class.
```java
SecretGenerator secretGenerator = new DefaultSecretGenerator();
String secret = secretGenerator.generate();
// secret = "BP26TDZUZ5SVPZJRIHCAUVREO5EWMHHV"
```

By default, this class generates secrets that are 32 characters long, but this number is configurable via

the class constructor.

```java
// Generates secrets that are 64 characters long
SecretGenerator secretGenerator = new DefaultSecretGenerator(64);
```



### Generating a QR code

Once a shared secret has been generated, this must be given to the user so they can add it to an MFA application, such as Google Authenticator. Whilst they could just enter the secret manually, a much better and more common option is to generate a QR code containing the secret (and other information), which can then be scanned by the application.

To generate such a QR code, first create a `dev.samstevens.totp.qr.QrData` instance with the relevant information.

```java
 QrData data = new QrData.Builder()
   .label("example@example.com")
   .secret(secret)
   .issuer("AppName")
   .algorithm(HashingAlgorithm.SHA1) // More on this below
   .digits(6)
   .period(30)
   .build();
```

Once you have a `QrData` object holding the relevant details, a PNG image of the code can be generated using the `dev.samstevens.totp.qr.ZxingPngQrGenerator` class.

```java
QrGenerator generator = new ZxingPngQrGenerator();
byte[] imageData = generator.generate(data)
```

The `generate` method returns a byte array of the raw image data. The mime type of the data that is generated by the generator can be retrieved using the `getImageMimeType` method.

```java
String mimeType = generator.getImageMimeType();
// mimeType = "image/png"
```

The image data can then be outputted to the browser, or saved to a temporary file to show it to the user.

#### Embedding the QR code within HTML

To avoid the QR code image having to be saved to disk, or passing the shared secret to another endpoint that generates and returns the image, it can be encoded in a [Data URI](https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Data_URIs), and embedded directly in the HTML served to the user.

```java
import static dev.samstevens.totp.util.Utils.getDataUriForImage;
...
String dataUri = getDataUriForImage(imageData, mimeType);
// dataUri = data:image/png;base64,iVBORw0KGgoAAAANSU...
```

The QR code image can now be embedded directly in HTML via the data URI. Below is an example using [Thymeleaf](https://www.thymeleaf.org/):

```html
<img th:src="${dataUri}" />
```



### Verifying one time passwords

After a user sets up their MFA, it's a good idea to get them to enter two of the codes generated by their app to verify the setup was successful. To verify a code submitted by the user, do the following:

```java
TimeProvider timeProvider = new SystemTimeProvider();
CodeGenerator codeGenerator = new DefaultCodeGenerator();
CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

// secret = the shared secret for the user
// code = the code submitted by the user
boolean successful = verifier.isValidCode(secret, code)
```

This same process is used when verifying the submitted code every time the user needs to in the future.

#### Using different hashing algorithms

By default, the `DefaultCodeGenerator` uses the SHA1 algorithm to generate/verify codes, but SHA256 and SHA512 are also supported. To use a different algorithm, pass in the desired `HashingAlgorithm` into the constructor:

```java
CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA512);
```

When verifying a given code, **you must use the same hashing algorithm** that was specified when the QR code was generated for the secret, otherwise the user submitted codes will not match.

#### Setting the time period and discrepancy

The one time password codes generated in the authenticator apps only last for a certain time period before they are re-generated, and most implementations of TOTP allow room for codes that have recently expired, or will only "become valid" soon in the future to be accepted as valid, to allow for a small time drift between the server and the authenticator app (discrepancy).

By default on a `DefaultCodeVerifier` the time period is set to the standard 30 seconds, and the discrepancy to 1, to allow a time drift of +/-30 seconds. These values can be changed by calling the appropriate setters:

```java
CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
// sets the time period for codes to be valid for to 60 seconds
verifier.setTimePeriod(60);

// allow codes valid for 2 time periods before/after to pass as valid
verifier.setAllowedTimePeriodDiscrepancy(2);
```

Like the hashing algorithm, **the time period must be the same** as the one specified when the QR code for the secret was created.

#### Setting how many digits long the generated codes are

Most TOTP implementations generate codes that are 6 digits long, but codes can have a length of any positive non-zero integer. The default number of digits in a code generated by a `DefaultCodeGenerator` instance is 6, but can be set to a different value by passing the number as the second parameter in the constructor:

```java
CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 4);
```

The above generator will generate codes of 4 digits, using the SHA1 algorithm.

Once again, **the number of digits must be the same** as what was specified when the QR code for the secret was created.



### Using different time providers

When verifying user submitted codes with a `DefaultCodeVerifier`, a `TimeProvider` is needed to get the current time (unix) time. In the example code above, a `SystemTimeProvider` is used, but this is not the only option.

#### Getting the time from the system

Most applications should be able to use the `SystemTimeProvider` class to provide the time, which gets the time from the system clock.  If the system clock is reliable, it is reccomended that this provider is used.


#### Getting the time from an NTP Server

If the system clock cannot be used to accurately get the current time, then you can fetch it from an NTP server with the `dev.samstevens.totp.time.NtpTimeProvider` class, passing in the NTP server hostname you wish you use.

```java
TimeProvider timeProvider = new NtpTimeProvider("pool.ntp.org");
```

The default timeout for the requests to the NTP server is 3 seconds, but this can be set by passing in the desired number of milliseconds as the second parameter in the constructor:

```java
TimeProvider timeProvider = new NtpTimeProvider("pool.ntp.org", 5000);
```



### Recovery Codes

Recovery codes can be used to allow users to gain access to their MFA protected account without providing a TOTP, bypassing the MFA process. This is usually given as an option to the user so that in the event of losing access to the device which they have registered the MFA secret with, they are still able to log in.

Usually, upon registering an account for MFA, several one-time use codes will be generated and presented to the user, with instructions to keep them very safe. When the user is presented with the prompt for a TOTP in the future, they can opt to enter one of the recovery codes instead to gain access to their account. 

Most of the logic needed for implementing recovery codes (storage, associating them with a user, checking for existance, etc) is implementation specific, but the codes themselves can be generated via this library.

```java
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
...
// Generate 16 random recovery codes
RecoveryCodeGenerator recoveryCodes = new RecoveryCodeGenerator();
String[] codes = recoveryCodes.generateCodes(16);
// codes = ["efixm-g7fds", "4u3uc-xij2u", "vba7i-3fpny", "cmxf4-shn26", ...]
```



## Running Tests

To run the tests for the library with Maven, run `mvn test`.




## License

This project is licensed under the [MIT license](https://opensource.org/licenses/MIT).
