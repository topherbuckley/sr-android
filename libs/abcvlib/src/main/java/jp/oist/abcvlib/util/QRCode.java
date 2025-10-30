package jp.oist.abcvlib.util;

import jp.oist.abcvlib.util.Logger;

import androidx.fragment.app.FragmentManager;

import jp.oist.abcvlib.fragments.QRCodeDisplayFragment;

public class QRCode {
    private FragmentManager fragmentManager;
    private int fragmentViewID;
    private boolean codevisible = false;

    public QRCode(FragmentManager fragmentManager, int fragmentViewID){
        this.fragmentManager = fragmentManager;
        this.fragmentViewID = fragmentViewID;
    }

    public void generate(String data2Encode){
        QRCodeDisplayFragment qrCodeDisplayFragment = new QRCodeDisplayFragment(data2Encode);
        fragmentManager.beginTransaction()
                .replace(fragmentViewID, qrCodeDisplayFragment)
                .setReorderingAllowed(true)
                .addToBackStack("qrCode")
                .commit();
        codevisible = true;
    }

    public void close(){
        if (codevisible){
            fragmentManager.popBackStack();
        }
        else {
            Logger.e("QRCode", "Attempted to close nonexistant QR Code");
        }
    }
}
