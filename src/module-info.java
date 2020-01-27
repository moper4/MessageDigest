module dzy.javafx.app.hash {
    requires org.bouncycastle.provider;
    requires javafx.controls;
    requires javafx.fxml;

    opens dzy.security.digest to java.base;
    opens dzy.javafx.app.hash;
    opens dzy.javafx.app.hash.fxml;
}