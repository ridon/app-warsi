package id.ridon.keude.views.swap;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.LightingColorFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import id.ridon.keude.Keude;
import id.ridon.keude.KeudeApp;
import id.ridon.keude.Preferences;
import id.ridon.keude.QrGenAsyncTask;
import id.ridon.keude.R;
import id.ridon.keude.Utils;
import id.ridon.keude.data.NewRepoConfig;
import id.ridon.keude.net.WifiStateChangeService;

import java.util.Locale;

public class WifiQrFragment extends Fragment {

    private static final int CONNECT_TO_SWAP = 1;

    private BroadcastReceiver onWifiChange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent i) {
            setUIFromWifi();
        }
    };

    private SwapProcessManager swapManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.swap_wifi_qr, container, false);
        ImageView qrImage = (ImageView)view.findViewById(R.id.wifi_qr_code);

        // Replace all blacks with the background blue.
        qrImage.setColorFilter(new LightingColorFilter(0xffffffff, getResources().getColor(R.color.swap_blue)));

        Button openQr = (Button)view.findViewById(R.id.btn_qr_scanner);
        openQr.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                IntentIntegrator integrator = new IntentIntegrator(WifiQrFragment.this);
                integrator.initiateScan();
            }
        });

        Button cancel = (Button)view.findViewById(R.id.btn_cancel_swap);
        cancel.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                swapManager.stopSwapping();
            }
        });
        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        swapManager = (SwapProcessManager)activity;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            if (scanResult.getContents() != null) {
                NewRepoConfig repoConfig = new NewRepoConfig(getActivity(), scanResult.getContents());
                if (repoConfig.isValidRepo()) {
                    startActivityForResult(new Intent(Keude.ACTION_ADD_REPO, Uri.parse(scanResult.getContents()), getActivity(), ConnectSwapActivity.class), CONNECT_TO_SWAP);
                } else {
                    Toast.makeText(getActivity(), "The QR code you scanned doesn't look like a swap code.", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == CONNECT_TO_SWAP && resultCode == Activity.RESULT_OK) {
            getActivity().finish();
        }
    }

    public void onResume() {
        super.onResume();
        setUIFromWifi();

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(onWifiChange,
                new IntentFilter(WifiStateChangeService.BROADCAST));
    }

    @TargetApi(14)
    private void setUIFromWifi() {

        if (TextUtils.isEmpty(KeudeApp.repo.address))
            return;

        String scheme = Preferences.get().isLocalRepoHttpsEnabled() ? "https://" : "http://";

        // the fingerprint is not useful on the button label
        String buttonLabel = scheme + KeudeApp.ipAddressString + ":" + KeudeApp.port;
        TextView ipAddressView = (TextView) getView().findViewById(R.id.device_ip_address);
        ipAddressView.setText(buttonLabel);

        /*
         * Set URL to UPPER for compact QR Code, Keude will translate it back.
         * Remove the SSID from the query string since SSIDs are case-sensitive.
         * Instead the receiver will have to rely on the BSSID to find the right
         * wifi AP to join. Lots of QR Scanners are buggy and do not respect
         * custom URI schemes, so we have to use http:// or https:// :-(
         */
        Uri sharingUri = Utils.getSharingUri(getActivity(), KeudeApp.repo);
        String qrUriString = (scheme + sharingUri.getHost()).toUpperCase(Locale.ENGLISH);
        if (sharingUri.getPort() != 80) {
            qrUriString += ":" + sharingUri.getPort();
        }
        qrUriString += sharingUri.getPath().toUpperCase(Locale.ENGLISH);
        boolean first = true;
        for (String parameterName : sharingUri.getQueryParameterNames()) {
            if (!parameterName.equals("ssid")) {
                if (first) {
                    qrUriString += "?";
                    first = false;
                } else {
                    qrUriString += "&";
                }
                qrUriString += parameterName.toUpperCase(Locale.ENGLISH) + "=" +
                    sharingUri.getQueryParameter(parameterName).toUpperCase(Locale.ENGLISH);
            }
        }

        Log.i("QRURI", qrUriString);

        // zxing requires >= 8
        // TODO: What about 7? I don't feel comfortable bumping the min version for this...
        // I would suggest show some alternate info, with directions for how to add a new repository manually.
        if (Build.VERSION.SDK_INT >= 8)
            new QrGenAsyncTask(getActivity(), R.id.wifi_qr_code).execute(qrUriString);

    }

}
