package com.bignerdranch.android.photogallery;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.google.gson.Gson;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xutils.common.Callback;
import org.xutils.http.RequestParams;
import org.xutils.x;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by leon on 5/23/16.
 */
public class PhotoGalleryFragment extends Fragment {

    private RecyclerView mPhotoRecyclerView;
    private static final String TAG = "PhotoGalleryFragment";
    private List<GalleryItem> mItems = new ArrayList<>();
    private int mPage = 1;
    private PhotoAdapter mAdapter;
    private GridLayoutManager mLayoutManager;
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;
    private static final String API_KEY = "ea0b23525b4dddb967276f34c2fab0e8";
    private static final String FETCH_RECENTS_METHOD = "flickr.photos.getRecent";
    private static final String SEARCH_METHOD = "flickr.photos.search";
    private static final Uri ENDPOINT = Uri.parse("https://api.flickr.com/services/rest/")
            .buildUpon()
            .appendQueryParameter("api_key", API_KEY)
            .appendQueryParameter("format", "json")
            .appendQueryParameter("nojsoncallback", "1")
            .appendQueryParameter("extras", "url_s")
            .build();
    private String mRequestMethod = SEARCH_METHOD;
    private SearchView mSearchView;
    private ProgressDialog mProgress;

    public static PhotoGalleryFragment newInstance() {
        return new PhotoGalleryFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);

        mPage = 1;
        fetchPhotos();
        Log.i(TAG, "Background thread started");

        Intent i = PollService.newIntent(getActivity());
        getActivity().startService(i);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_photo_gallery, container, false);
        mPhotoRecyclerView = (RecyclerView) view.findViewById(R.id.fragment_photo_gallery_recycler_view);
        mLayoutManager = new GridLayoutManager(getActivity(), 3);
        mPhotoRecyclerView.setLayoutManager(mLayoutManager);
        mPhotoRecyclerView.addOnScrollListener(new EndlessRecyclerOnScrollListener(mLayoutManager) {

            @Override
            public void onLoadMore(int current_page) {
                Log.i(TAG, "onLoadMore: ");
                mPage++;
                mPage = 1;
                fetchPhotos();
            }
        });
        setupAdapter();
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_photo_gallery, menu);

        String query = QueryPreferences.getStoredQuery(getActivity());
        final MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        mSearchView = (SearchView) searchItem.getActionView();
        if (query != null) {
            mSearchView.setIconified(false);
            mSearchView.setQuery(query, false);
            mSearchView.clearFocus();
        }

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmit: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);
                mPage = 1;
                fetchPhotos();
                mSearchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "QueryTextChange: " + newText);
                return false;
            }
        });

        mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                Log.d(TAG, "onClose: ");
                QueryPreferences.setStoredQuery(getActivity(), null);
                mPage = 1;
                fetchPhotos();
                return false;
            }
        });

//        searchView.setOnSearchClickListener(new View.OnClickListener() {
//
//            @Override
//            public void onClick(View v) {
//                String query = QueryPreferences.getStoredQuery(getActivity());
//                searchView.setQuery(query, false);
//            }
//        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                mPage = 1;
                fetchPhotos();
                mSearchView.setQuery("", false);
                mSearchView.setIconified(true);

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showLoading() {
        mProgress = new ProgressDialog(getActivity());
        mProgress.setTitle("Loading");
        mProgress.setMessage("Wait while loading...");
        mProgress.show();
    }

    private void dismissLoading() {
        mProgress.dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }

    private void setupAdapter() {
        if (isAdded()) {
            if (mAdapter == null) {
                mAdapter = new PhotoAdapter();
                mPhotoRecyclerView.setAdapter(mAdapter);
            } else {
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    String buildUrl() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        Uri.Builder uriBuilder = ENDPOINT.buildUpon().appendQueryParameter("method", FETCH_RECENTS_METHOD);
        if (query != null) {
            uriBuilder = ENDPOINT.buildUpon().appendQueryParameter("method", SEARCH_METHOD);
            uriBuilder.appendQueryParameter("text", query);
        }
        uriBuilder.appendQueryParameter("page", String.valueOf(mPage));
        return uriBuilder.build().toString();
    }

    void fetchPhotos() {
        showLoading();
        String url = buildUrl();
        RequestParams params = new RequestParams(url);
        x.http().get(params, new Callback.CommonCallback<String>() {
            @Override
            public void onSuccess(String result) {
                Log.i(TAG, "Received JSON: " + result);

                try {
                    JSONObject jsonBody = new JSONObject(result);
                    parseItems(jsonBody);
                } catch (JSONException e) {
                    Log.i(TAG, "Failed to parse JSON", e);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to fetch items", e);
                }
                setupAdapter();
                if (mPage == 1) {
                    mLayoutManager.scrollToPosition(0);
                }
                dismissLoading();
            }

            @Override
            public void onError(Throwable ex, boolean isOnCallback) {
                dismissLoading();
            }

            @Override
            public void onCancelled(CancelledException cex) {
                dismissLoading();
            }

            @Override
            public void onFinished() {
                dismissLoading();
            }
        });
    }

    private void parseItems(JSONObject jsonBody) throws IOException, JSONException {
        JSONObject photosJsonObject = jsonBody.getJSONObject("photos");
        JSONArray photoJsonArray = photosJsonObject.getJSONArray("photo");

        if (mPage == 1) {
            mItems.clear();
        }
        Gson gson = new Gson();
        for (int i = 0; i < photoJsonArray.length(); i++) {
            JSONObject photoJsonObject = photoJsonArray.getJSONObject(i);
            GalleryItem item = gson.fromJson(photoJsonObject.toString(), GalleryItem.class);
            mItems.add(item);
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder {
        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);
            mItemImageView = (ImageView) itemView.findViewById(R.id.fragment_photo_gallery_image_view);
        }

        public void bindObject(Object object) {
            GalleryItem galleryItem = (GalleryItem) object;
//            ImageOptions options = new ImageOptions.Builder().setLoadingDrawableId(R.drawable.sample).build();
//            x.image().bind(mItemImageView, galleryItem.getUrl(), options);

            Picasso.with(getActivity()).load(galleryItem.getUrl()).placeholder(R.drawable.sample).into(mItemImageView);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder> {


        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, parent, false);
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {
            GalleryItem galleryItem = mItems.get(position);
            Drawable placeholder = getResources().getDrawable(R.drawable.sample);
            holder.bindObject(galleryItem);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }
}
