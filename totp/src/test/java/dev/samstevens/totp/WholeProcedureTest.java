package dev.samstevens.totp;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;

/**
 * 完整流程测试
 * 生成一个otpauth://totp的协议内容，通过Google Authenticator or Microsoft Authenticator应用扫码后，根据生成的随机码进行验证
 */
public class WholeProcedureTest {
    public static void main(String[] args) throws CodeGenerationException, InterruptedException {
        // init();
        vefify();
    }

    private static void init() {
        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();
        System.out.println(secret);

        QrData data = new QrData.Builder()
            .label("example@example.com")
            .secret(secret)
            .issuer("AppName")
            .algorithm(HashingAlgorithm.SHA1) // More on this below
            .digits(6)
            .period(30)
            .build();
        System.out.println(data.getUri());
    }

    private static void vefify() throws CodeGenerationException, InterruptedException {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // 二维码内容：otpauth://totp/example%40example.com?secret=CH4772YYRSD7O5E7KQRZMHNRRRLSASCW&issuer=AppName&algorithm=SHA1&digits=6&period=30
        String secret = "CH4772YYRSD7O5E7KQRZMHNRRRLSASCW";
        // String userCode = "408057";
        // System.out.println(verifier.isValidCode(secret, userCode));
        while (true) {
            long currentBucket = Math.floorDiv(timeProvider.getTime(), 30);
            long left = 30 - (System.currentTimeMillis() / 1000) % 30;
            String generate = codeGenerator.generate(secret, currentBucket);
            System.out.println("secret code : " + generate + ", change in "  + left + " seconds");
            Thread.sleep(1000);
        }
    }
}
