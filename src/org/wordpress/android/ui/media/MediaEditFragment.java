
package org.wordpress.android.ui.media;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.android.volley.toolbox.NetworkImageView;

import org.xmlrpc.android.ApiHelper;

import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.models.Blog;

public class MediaEditFragment extends SherlockFragment {

    private static final String ARGS_MEDIA_ID = "media_id";
    private static final String BUNDLE_MEDIA_ID = "media_id";
    public static final String TAG = "MediaEditFragment"; // also appears in the layouts, from the strings.xml
    
    private NetworkImageView mImageView;
    private EditText mTitleView;
    private EditText mCaptionView;
    private EditText mDescriptionView;
    private Button mSaveButton;
    
    private MediaEditFragmentCallback mCallback;

    private boolean mIsMediaUpdating = false;

    private String mMediaId;
    private ScrollView mScrollView;

    public interface MediaEditFragmentCallback {
        public void onResume(Fragment fragment);
        public void onPause(Fragment fragment);
        public void onSavedEdit(String mediaId, boolean result);
    }

    public static MediaEditFragment newInstance(String mediaId) {
        MediaEditFragment fragment = new MediaEditFragment();

        Bundle args = new Bundle();
        args.putString(ARGS_MEDIA_ID, mediaId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }
    
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mCallback = (MediaEditFragmentCallback) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement " + MediaEditFragmentCallback.class.getCanonicalName());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mCallback.onResume(this);
        getView().post(new Runnable() {
            
            @Override
            public void run() {
                loadMedia(getMediaId());
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        mCallback.onPause(this);
    }

    public String getMediaId() {
        if (mMediaId != null) {
            return mMediaId;
        } else if (getArguments() != null) {
            mMediaId = getArguments().getString(ARGS_MEDIA_ID); 
            return mMediaId;
        } else {
            return null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mScrollView = (ScrollView) inflater.inflate(R.layout.media_edit_fragment, container, false);

        mTitleView = (EditText) mScrollView.findViewById(R.id.media_edit_fragment_title);
        mImageView = (NetworkImageView) mScrollView.findViewById(R.id.media_edit_fragment_image);
        mCaptionView = (EditText) mScrollView.findViewById(R.id.media_edit_fragment_caption);
        mDescriptionView = (EditText) mScrollView.findViewById(R.id.media_edit_fragment_description);
        
        mSaveButton = (Button) mScrollView.findViewById(R.id.media_edit_save_button);
        mSaveButton.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                editMedia();
            }
        });

        restoreState(savedInstanceState);
        
        return mScrollView;
    }
    
    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BUNDLE_MEDIA_ID)) {
                mMediaId = savedInstanceState.getString(BUNDLE_MEDIA_ID);
            }
        }
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveState(outState);
    }

    private void saveState(Bundle outState) {
        outState.putString(BUNDLE_MEDIA_ID, getMediaId());
    }

    public void loadMedia(String mediaId) {
        mMediaId = mediaId;
        Blog blog = WordPress.getCurrentBlog();

        if (blog != null && getActivity() != null) {
            String blogId = String.valueOf(blog.getBlogId());

            Cursor cursor;

            // if the id is null, get the first media item in the database
            if (mMediaId == null) {
                cursor = WordPress.wpDB.getFirstMediaFileForBlog(blogId);
            } else {
                cursor = WordPress.wpDB.getMediaFile(blogId, mMediaId);
            }

            refreshViews(cursor);
            cursor.close();
        }
    }

    public void hideKeyboard() {
        if (getActivity() != null) {
            InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
    
    public void editMedia() {
        hideKeyboard();
        
        final String mediaId = this.getMediaId();
        final String title = mTitleView.getText().toString();
        final String description = mDescriptionView.getText().toString();
        final Blog currentBlog = WordPress.getCurrentBlog();
        final String caption = mCaptionView.getText().toString();

        ApiHelper.EditMediaItemTask task = new ApiHelper.EditMediaItemTask(mediaId, title,
                description, caption, 
                new ApiHelper.EditMediaItemTask.Callback() {

                    @Override
                    public void onSuccess() {
                        String blogId = String.valueOf(currentBlog.getBlogId());
                        WordPress.wpDB.updateMediaFile(blogId, mediaId, title, description, caption);

                        if (getActivity() != null)
                            Toast.makeText(getActivity(), R.string.media_edit_success, Toast.LENGTH_LONG).show();

                        setMediaUpdating(false);
                        mCallback.onSavedEdit(mediaId, true);
                    }

                    @Override
                    public void onFailure() {
                        if (getActivity() != null)
                            Toast.makeText(getActivity(), R.string.media_edit_failure, Toast.LENGTH_LONG).show();
    
                        setMediaUpdating(false);
                        mCallback.onSavedEdit(mediaId, false);
                    }
                });

        List<Object> apiArgs = new ArrayList<Object>();
        apiArgs.add(currentBlog);

        if (!isMediaUpdating()) {
            setMediaUpdating(true); 
            task.execute(apiArgs);
        }

    }

    private void setMediaUpdating(boolean isUpdating) {
        mIsMediaUpdating = isUpdating;
        mSaveButton.setEnabled(!isUpdating);
    }
    
    private boolean isMediaUpdating() {
        return mIsMediaUpdating;
    }

    private void refreshViews(Cursor cursor) {
        if (!cursor.moveToFirst()) {
            // hide all the views
            for (int i = 0; i < mScrollView.getChildCount();  i++){
                View view = mScrollView.getChildAt(i);
                view.setVisibility(View.GONE);
            }
            return;
        }
        
        for (int i = 0; i < mScrollView.getChildCount();  i++){
            View view = mScrollView.getChildAt(i);
            view.setVisibility(View.VISIBLE);
        }
        
        mScrollView.scrollTo(0, 0);
        
        mMediaId = cursor.getString(cursor.getColumnIndex("mediaId"));
        mTitleView.setText(cursor.getString(cursor.getColumnIndex("title")));
        mTitleView.requestFocus();
        mTitleView.setSelection(mTitleView.getText().length());
        mCaptionView.setText(cursor.getString(cursor.getColumnIndex("caption")));
        mDescriptionView.setText(cursor.getString(cursor.getColumnIndex("description")));

        String imageUrl = cursor.getString(cursor.getColumnIndex("fileURL"));
        if (MediaUtils.isValidImage(imageUrl)) {

            int width = cursor.getInt(cursor.getColumnIndex("width"));
            int height = cursor.getInt(cursor.getColumnIndex("height"));

            float screenWidth = getActivity().getResources().getDisplayMetrics().widthPixels;

            View parentView = (View) mImageView.getParent();

            // differentiating between tablet and phone
            if (this.isInLayout()) {
                screenWidth = parentView.getMeasuredWidth();
            } else {
                screenWidth = getActivity().getResources().getDisplayMetrics().widthPixels;
            }
            float screenHeight = getActivity().getResources().getDisplayMetrics().heightPixels;

            mImageView.setImageUrl(imageUrl + "?w=" + screenWidth, WordPress.imageLoader);
            mImageView.setVisibility(View.VISIBLE);

            if (width > screenWidth) {
                height = (int) (height / (width / screenWidth));
            } else if (height > screenHeight) {
                width = (int) (width / (height / screenHeight));
            }
            mImageView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height));

        } else {
            mImageView.setVisibility(View.GONE);
        }
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!isInLayout()) {
            inflater.inflate(R.menu.media_edit, menu);
        }
    }
    

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (!isInLayout()) {
            menu.findItem(R.id.menu_refresh).setVisible(false);
            menu.findItem(R.id.menu_new_media).setVisible(false);
            menu.findItem(R.id.menu_search).setVisible(false);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_save_media) {
            editMedia();
        }
        return super.onOptionsItemSelected(item);
    }
}
