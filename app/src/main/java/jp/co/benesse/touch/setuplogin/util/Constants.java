package jp.co.benesse.touch.setuplogin.util;

public class Constants {
    public static final String BC_PASSWORD_HIT_FLAG = "bc_password_hit";

    public static final String DCHA_PACKAGE = "jp.co.benesse.dcha.dchaservice";
    public static final String DCHA_SERVICE = DCHA_PACKAGE + ".DchaService";

    public static final String[] CT2_MODELS = {
            "TAB-A03-BS", // CT2S
            "TAB-A03-BR", // CT2K
            "TAB-A03-BR2" // CT2L
    };

    public static final int REQUEST_DOWNLOAD_CHECK_FILE = 0;
    public static final int REQUEST_DOWNLOAD_APK = 1;

    public static final String URL_CHECK = "https://raw.githubusercontent.com/Kobold831/Server/main/production/json/Check.json";
}
