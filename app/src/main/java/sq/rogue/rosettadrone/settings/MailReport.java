package sq.rogue.rosettadrone.settings;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.mail.Session;

import dji.sdk.media.MediaFile;
import sq.rogue.rosettadrone.MainActivity;


//public class MailReport extends AsyncTask<Void,Void,Void> { // extends AppCompatActivity {
public class MailReport extends javax.mail.Authenticator{

    private final String TAG = MainActivity.class.getSimpleName();
    private MainActivity parent;

    private ContentResolver contentResolver;
    private  File imageDirectory;

    //---------------------------------------------------------------------------------------
    public  MailReport(MainActivity parent, File dir, ContentResolver resolver)
    {
        imageDirectory = dir;
        this.parent = parent;
        contentResolver = resolver;
    }

    //---------------------------------------------------------------------------------------
    private ArrayList<Uri> getUriListForImages(String filename) throws Exception
    {
        ArrayList<Uri> myList = new ArrayList<Uri>();
        String[] fileList = imageDirectory.list();
        if(fileList.length != 0) {
            for(int i=0; i<fileList.length; i++)
            {
                int err = filename.compareTo(fileList[i]);
                Log.d(TAG,"Files: "+filename+"  ->  "+fileList[i]+"  err: "+err);

                if(err==0) {
                    Log.d(TAG,"Found... ");
                    try {
                        ContentValues values = new ContentValues(7);
                        values.put(MediaStore.Images.Media.TITLE, fileList[i]);
                        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileList[i]);
                        values.put(MediaStore.Images.Media.DATE_TAKEN, new Date().getTime());
                        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        values.put(MediaStore.Images.ImageColumns.BUCKET_ID, imageDirectory.getAbsolutePath().hashCode());
                        values.put(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, fileList[i]);
                        values.put("_data", imageDirectory.getAbsolutePath() + "/" + fileList[i]);
                        Uri uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                        myList.add(uri);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return myList;
    }

    //---------------------------------------------------------------------------------------
    public Intent createEmail(List<String> to, String subject, String message, double lat, double lon, double alt, double head, String filename)
    {
        message+="\nAt position Lat: "+lat+" Lon: "+lon+" Alt: "+alt + " Heading: "+head;
//        message+="\nURL: https://www.google.com/maps/search/?api=1&query="+lat+","+lon+"\n";
        message+="\nURL: http://maps.google.com/maps?z=1&t=h&q=loc:"+lat+"+"+lon+"\n";

        Intent email = new  Intent(Intent.ACTION_SEND_MULTIPLE);
        email.setData(Uri.parse("mailto:")); // only email apps should handle this

        for(String address:to){
            Log.d(TAG,"Address: "+ address);
            email.putExtra(Intent.EXTRA_EMAIL, new String[]{address});
        }
        email.putExtra(Intent.EXTRA_SUBJECT, subject);
        email.putExtra(Intent.EXTRA_TEXT, message);

        try {
            ArrayList<Uri> uriList = getUriListForImages(filename);
            email.putExtra(Intent.EXTRA_STREAM, uriList);
        } catch (Exception e){
            Log.d(TAG,"Missing file: "+ e.toString());
        }

        //need this to prompts email client only
        email.setType("message/rfc822");
        //  email.setType("text/html");

        return email;
    }
//---------------------------------------------------------------------------------------------

}