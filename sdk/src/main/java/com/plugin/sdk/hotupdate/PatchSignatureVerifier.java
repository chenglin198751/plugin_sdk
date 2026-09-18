package com.plugin.sdk.hotupdate;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.plugin.sdk.utils.AppLogUtils;

import java.security.MessageDigest;

/**
 * 补丁 APK 签名校验：校验「补丁签名证书 == 宿主自身签名证书」。
 * <p>
 * 采用「补丁与宿主同签名」模型：插件用与宿主相同的签名（gradle 默认 debug 签名）
 * 打包，验签时比对补丁签名和宿主签名是否一致。任何一字节被篡改，签名即失效。
 * <p>
 * 分版本策略：
 * <ul>
 *   <li>API 28+：用 {@link android.content.pm.SigningInfo} 读取签名证书（V2/V3）。</li>
 *   <li>API &lt; 28：退化为 V1 签名比对（gradle 默认 V1+V2 双签，低版本走 V1）。</li>
 * </ul>
 */
public final class PatchSignatureVerifier {

    private static final String TAG = "SignatureVerifier";

    private PatchSignatureVerifier() {
    }

    public static boolean verify(Context context, String apkPath) {
        AppLogUtils.i(TAG, "verify 开始, SDK_INT=" + Build.VERSION.SDK_INT + ", apk=" + apkPath);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                boolean r = verifyViaSigningInfo(context, apkPath);
                AppLogUtils.i(TAG, "verifyViaSigningInfo 结果 = " + r);
                return r;
            }
            boolean r = verifyViaV1(context, apkPath);
            AppLogUtils.i(TAG, "verifyViaV1 结果 = " + r);
            return r;
        } catch (Throwable t) {
            AppLogUtils.e(TAG, "verify 异常", t);
            return false;
        }
    }

    /** API 28+：读取 V2/V3 签名证书，比对宿主。 */
    private static boolean verifyViaSigningInfo(Context context, String apkPath) throws Exception {
        PackageManager pm = context.getPackageManager();

        PackageInfo patchInfo = pm.getPackageArchiveInfo(
                apkPath, PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo hostInfo = pm.getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);

        AppLogUtils.i(TAG, "patchInfo=" + (patchInfo == null ? "null" : "ok")
                + ", patchInfo.signingInfo="
                + (patchInfo != null && patchInfo.signingInfo != null ? "ok" : "null"));
        AppLogUtils.i(TAG, "hostPkg=" + context.getPackageName()
                + ", hostInfo.signingInfo="
                + (hostInfo != null && hostInfo.signingInfo != null ? "ok" : "null"));

        if (patchInfo == null || patchInfo.signingInfo == null) {
            return false;
        }
        if (hostInfo == null || hostInfo.signingInfo == null) {
            return false;
        }

        Signature[] patchSigners = patchInfo.signingInfo.getApkContentsSigners();
        Signature[] hostSigners = hostInfo.signingInfo.getApkContentsSigners();

        AppLogUtils.i(TAG, "patchSigners 数量=" + (patchSigners == null ? 0 : patchSigners.length)
                + ", hostSigners 数量=" + (hostSigners == null ? 0 : hostSigners.length));
        if (patchSigners != null) {
            for (Signature s : patchSigners) {
                AppLogUtils.i(TAG, "patch 证书 SHA256=" + sha256Hex(s.toByteArray()));
            }
        }
        if (hostSigners != null) {
            for (Signature s : hostSigners) {
                AppLogUtils.i(TAG, "host 证书 SHA256=" + sha256Hex(s.toByteArray()));
            }
        }

        boolean r = shareSigner(patchSigners, hostSigners);
        AppLogUtils.i(TAG, "shareSigner 结果 = " + r);
        return r;
    }

    /** API < 28：读取 V1 签名，比对宿主。 */
    private static boolean verifyViaV1(Context context, String apkPath) throws Exception {
        PackageManager pm = context.getPackageManager();

        PackageInfo patchInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES);
        PackageInfo hostInfo = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);

        AppLogUtils.i(TAG, "patchInfo(V1)=" + (patchInfo == null ? "null" : "ok")
                + ", hostInfo(V1)=" + (hostInfo == null ? "null" : "ok"));

        if (patchInfo == null || hostInfo == null) {
            return false;
        }
        boolean r = shareSigner(patchInfo.signatures, hostInfo.signatures);
        AppLogUtils.i(TAG, "shareSigner(V1) 结果 = " + r);
        return r;
    }

    /** 判断两组签名里是否存在相同的证书（按 SHA-256 指纹比较）。 */
    private static boolean shareSigner(Signature[] a, Signature[] b) {
        if (a == null || b == null) {
            return false;
        }
        for (Signature sa : a) {
            String fa = sha256Hex(sa.toByteArray());
            for (Signature sb : b) {
                if (fa.equals(sha256Hex(sb.toByteArray()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
