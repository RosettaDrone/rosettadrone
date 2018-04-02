package sq.rogue.rosettadrone;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

public class SharedPreferencesTest {

    @Before
    public void before() {
        final SharedPreferences sharedPreferences = Mockito.mock(SharedPreferences.class);
        final Context context = Mockito.mock(Context.class);
        Mockito.when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences);
    }

//    @Test

}
