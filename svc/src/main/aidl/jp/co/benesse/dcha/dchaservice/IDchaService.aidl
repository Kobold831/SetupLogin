package jp.co.benesse.dcha.dchaservice;

interface IDchaService {
    boolean installApp(String path, int installFlag) = 7;
    void setSetupStatus(int status) = 10;
    void hideNavigationBar(boolean hide) = 21;
}
