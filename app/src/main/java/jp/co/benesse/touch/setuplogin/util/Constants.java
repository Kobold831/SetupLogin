package jp.co.benesse.touch.setuplogin.util;

public class Constants {
    public static final String DCHA_PACKAGE = "jp.co.benesse.dcha.dchaservice";
    public static final String DCHA_SERVICE = DCHA_PACKAGE + ".DchaService";

    public static final String[] CT2_MODELS = {
            "TAB-A03-BS", // CT2S
            "TAB-A03-BR", // CT2K
            "TAB-A03-BR2" // CT2L
    };

    public static final int REQUEST_DOWNLOAD_CHECK_FILE = 0;
    public static final int REQUEST_DOWNLOAD_APK = 1;

    public static final String FILE_CHECK_JSON = "AppList.json";
    public static final String FILE_APK = "base.apk";
    public static final String TMP_EXT = ".tmp";
    public static final String URL_CHECK = "https://raw.githubusercontent.com/Kobold831/Server/main/production/" + FILE_CHECK_JSON;

    public static final String BUILD_MODEL_CT3 = "TAB-A03-BR3";
    public static final String BUILD_MODEL_CTX = "TAB-A05-BD";
    public static final String BUILD_MODEL_CTZ = "TAB-A05-BA1";

    public static final String MODEL_CT2 = "CT2";
    public static final String MODEL_CT3 = "CT3";
    public static final String MODEL_CTX = "CTX";
    public static final String MODEL_CTZ = "CTZ";

    public static final String JSON_APP_LIST = "appList";
    public static final String JSON_TARGET_MODEL = "targetModel";
    public static final String JSON_PACKAGE_NAME = "packageName";
    public static final String JSON_NAME = "name";
    public static final String JSON_DESCRIPTION = "description";
    public static final String JSON_URL = "url";
    public static final String JSON_APP_OPEN = "appOpen";
}
