package org.kaleta.firebase;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseException;
import org.junit.jupiter.api.Test;
import org.kaleta.model.FirebaseCompany;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeFirebaseStoreTest
{
    @Test
    void toChildMap_keepsEveryCompanyThatCanBeConverted()
    {
        FirebaseCompany nvidia = new FirebaseCompany();
        FirebaseCompany amd = new FirebaseCompany();

        Map<String, FirebaseCompany> result = RealtimeFirebaseStore.toChildMap(
                snapshot(child("NVDA", nvidia), child("AMD", amd)),
                FirebaseCompany.class);

        assertThat(result, is(Map.of("NVDA", nvidia, "AMD", amd)));
    }

    @Test
    void toChildMap_skipsPlaceholderCompanyThatIsStillAPlainString()
    {
        FirebaseCompany nvidia = new FirebaseCompany();
        FirebaseCompany amd = new FirebaseCompany();

        Map<String, FirebaseCompany> result = RealtimeFirebaseStore.toChildMap(
                snapshot(child("NVDA", nvidia), placeholderChild("NEWCO"), child("AMD", amd)),
                FirebaseCompany.class);

        assertThat(result, is(Map.of("NVDA", nvidia, "AMD", amd)));
    }

    private DataSnapshot snapshot(DataSnapshot... children)
    {
        DataSnapshot snapshot = mock(DataSnapshot.class);
        when(snapshot.getChildren()).thenReturn(List.of(children));
        return snapshot;
    }

    private DataSnapshot child(String key, FirebaseCompany value)
    {
        DataSnapshot child = mock(DataSnapshot.class);
        when(child.getKey()).thenReturn(key);
        when(child.getValue(FirebaseCompany.class)).thenReturn(value);
        return child;
    }

    private DataSnapshot placeholderChild(String key)
    {
        DataSnapshot child = mock(DataSnapshot.class);
        when(child.getKey()).thenReturn(key);
        when(child.getValue(FirebaseCompany.class)).thenThrow(new DatabaseException(
                "Can't convert object of type java.lang.String to type org.kaleta.model.FirebaseCompany"));
        return child;
    }
}
