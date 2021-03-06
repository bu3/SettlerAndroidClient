package com.settler;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.widget.RecyclerView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.settler.api.Property;
import com.settler.api.events.PropertiesAvailableEvent;

import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ActivityController;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import edu.emory.mathcs.backport.java.util.Arrays;

import static com.jayway.awaitility.Awaitility.await;
import static com.settler.Constants.ExtrasKeys.PROPERTIES_LIST;
import static com.settler.Constants.IntentFilterKeys.PROPERTIES_LIST_REQUEST;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
public class MainActivityTest extends PropertyBaseTest {

    @Test
    public void startServiceOnResume() throws Exception {
        ActivityController<MainActivity> activityController = Robolectric.buildActivity(MainActivity.class).create(null);

        MainActivity mainActivity = activityController.get();
        activityController.start();

        assertNull(Shadows.shadowOf(mainActivity).getNextStartedService());

        activityController.resume();

        Intent expectedIntent = new Intent(PROPERTIES_LIST_REQUEST);
        expectedIntent.setPackage(mainActivity.getPackageName());
        assertEquals(Shadows.shadowOf(mainActivity).getNextStartedService(), expectedIntent);
    }

    @Test
    /**
     * Should not start the service on onResume when a previous list of properties was recently downloaded
     */
    public void shouldNotStartServicePreviousStateNotEmpty() throws Exception {

        final Bundle bundle = getMockedBundle(1);

        ActivityController<MainActivity> activityController = Robolectric.buildActivity(MainActivity.class).create(bundle).start().resume();

        MainActivity mainActivity = activityController.get();

        assertNull(Shadows.shadowOf(mainActivity).getNextStartedService());
    }

    @Test
    public void shouldSaveStateWithNotNull() {

        //Starts the activity with no state
        final ActivityController<MainActivity> activityController = Robolectric.buildActivity(MainActivity.class);
        final MainActivity mainActivity = activityController.get();

        activityController.create().start().resume();

        //fake Service eventBus post
        final PropertiesAvailableEvent event = new PropertiesAvailableEvent(buildPropertiesList(2));
        mainActivity.propertiesAvailable(event);
        activityController.visible().get(); //lifecycle complete

        //pause the activity
        final Bundle bundle = new Bundle();
        activityController.pause().saveInstanceState(bundle);

        Parcelable[] parcelableArray = bundle.getParcelableArray(PROPERTIES_LIST);
        assertNotNull(parcelableArray);
        assertEquals(event.getProperties(), Arrays.asList(parcelableArray));

    }

    @Test
    public void shouldNotSaveStateNull() {

        //Starts the activity with no state
        final ActivityController<MainActivity> activityController = Robolectric.buildActivity(MainActivity.class);
        activityController.create().start().resume().visible().get();

        //pause the activity
        final Bundle bundle = new Bundle();
        activityController.pause().saveInstanceState(bundle);

        Parcelable[] parcelableArray = bundle.getParcelableArray(PROPERTIES_LIST);
        assertNull(parcelableArray);

    }

    @Test
    public void handleNewPropertyList() throws Exception {

        ActivityController<MainActivity> activityController = Robolectric.buildActivity(MainActivity.class);
        final MainActivity mainActivity = activityController.create().start().resume().get();

        //fake Service eventBus post
        List<Property> properties = buildPropertiesList(2);
        properties.get(0).setAddress("New Address");
        final PropertiesAvailableEvent event = new PropertiesAvailableEvent(properties);
        mainActivity.propertiesAvailable(event);

        await().atMost(5, TimeUnit.SECONDS).until(propertiesReceived(mainActivity), CoreMatchers.equalTo(Integer.valueOf(2)));
        await().atMost(5, TimeUnit.SECONDS).until(propertiesReceivedTitle(mainActivity), CoreMatchers.equalTo("New Address"));


        //get event bus
        //post an PropertiesAvailableEvent
        //assert that the activity handled it (1 * propertiesAvailable)

    }

    private Callable<Integer> propertiesReceived(final MainActivity mainActivity) {
        return new Callable<Integer>() {
            public Integer call() throws Exception {
                RecyclerView.Adapter adapter = ((RecyclerView) mainActivity.findViewById(R.id.recyclerView)).getAdapter();
                return adapter == null ? 0 : adapter.getItemCount();
            }
        };
    }

    private Callable<String> propertiesReceivedTitle(final MainActivity mainActivity) {
        return new Callable<String>() {
            public String call() throws Exception {
                RecyclerView recyclerView = (RecyclerView) mainActivity.findViewById(R.id.recyclerView);
                RecyclerView.Adapter adapter = recyclerView.getAdapter();
                RecyclerView.ViewHolder viewHolder = adapter.createViewHolder(recyclerView, 0);
                adapter.bindViewHolder(viewHolder, 0);
                TextView address = ((TextView)viewHolder.itemView.findViewById(R.id.property_address));
                return address == null ? null : address.getText().toString();
            }
        };
    }
}
