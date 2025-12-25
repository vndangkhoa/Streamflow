package com.streamflix.tv.ui.details

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.app.DetailsSupportFragmentBackgroundController
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.streamflix.tv.R
import com.streamflix.tv.data.MyListManager
import com.streamflix.tv.data.api.ApiClient
import com.streamflix.tv.data.model.Movie
import com.streamflix.tv.ui.browse.CardPresenter
import com.streamflix.tv.ui.playback.PlaybackActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Details Fragment using Leanback DetailsSupportFragment
 * Shows movie details with play button and related movies
 */
class DetailsFragment : DetailsSupportFragment() {

    companion object {
        private const val EXTRA_MOVIE = "extra_movie"
        
        private const val ACTION_PLAY = 1L
        private const val ACTION_ADD_LIST = 2L
        
        fun newInstance(movie: Movie): DetailsFragment {
            return DetailsFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(EXTRA_MOVIE, movie)
                }
            }
        }
    }

    private lateinit var movie: Movie
    private lateinit var backgroundController: DetailsSupportFragmentBackgroundController
    private lateinit var presenterSelector: ClassPresenterSelector
    private lateinit var rowsAdapter: ArrayObjectAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        movie = arguments?.getSerializable(EXTRA_MOVIE) as? Movie ?: run {
            requireActivity().finish()
            return
        }

        setupBackgroundController()
        setupAdapter()
        loadMovieDetails()
    }

    private fun setupBackgroundController() {
        backgroundController = DetailsSupportFragmentBackgroundController(this).apply {
            enableParallax()
        }
    }

    private fun setupAdapter() {
        presenterSelector = ClassPresenterSelector()
        
        // Full width details presenter for the main details row
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(
            DetailsDescriptionPresenter()
        ).apply {
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.primary_dark)
            
            // Set up action click listener
            setOnActionClickedListener { action ->
                when (action.id) {
                    ACTION_PLAY -> playMovie()
                    ACTION_ADD_LIST -> addToList()
                }
            }
        }
        
        presenterSelector.addClassPresenter(DetailsOverviewRow::class.java, detailsPresenter)
        presenterSelector.addClassPresenter(ListRow::class.java, ListRowPresenter())
        
        rowsAdapter = ArrayObjectAdapter(presenterSelector)
        adapter = rowsAdapter
    }

    private fun loadMovieDetails() {
        lifecycleScope.launch {
            try {
                // Fetch full movie details from API
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getMovieDetails(movie.slug)
                }

                // Update movie with full details if available
                val detail = response.movie ?: run {
                    // Try to use flat structure fields
                    com.streamflix.tv.data.model.MovieDetail(
                        slug = response.slug,
                        name = response.name ?: response.title,
                        content = response.content ?: response.description,
                        director = response.director,
                        actor = response.actor ?: response.cast,
                        year = response.year,
                        quality = response.quality
                    )
                }

                // Merge detail data into movie object
                movie = movie.copy(
                    name = detail.name ?: movie.name,
                    title = detail.title ?: movie.title,
                    content = detail.content ?: detail.description ?: movie.content,
                    year = if (detail.year != null && detail.year != 0) detail.year else movie.year,
                    quality = detail.quality ?: movie.quality,
                    director = parseAnyToList(detail.director) ?: movie.director,
                    actor = parseAnyToList(detail.actor ?: detail.cast) ?: movie.actor
                )

                setupDetailsOverviewRow()
                setupRelatedMoviesRow()
                loadBackgroundImage()
            } catch (e: Exception) {
                e.printStackTrace()
                // Use the basic movie data we already have
                setupDetailsOverviewRow()
                loadBackgroundImage()
            }
        }
    }

    private fun setupDetailsOverviewRow() {
        val row = DetailsOverviewRow(movie)

        // Load poster image
        val imageUrl = movie.getPosterImage()
        val context = context ?: return
        
        if (imageUrl.isNotEmpty()) {
            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .into(object : CustomTarget<Bitmap>(200, 300) {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        if (isAdded) {
                            row.setImageBitmap(requireContext(), resource)
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }

        // Add action buttons
        val actionAdapter = ArrayObjectAdapter()
        actionAdapter.add(Action(ACTION_PLAY, getString(R.string.play), null))
        actionAdapter.add(Action(ACTION_ADD_LIST, getString(R.string.add_to_list), null))
        row.actionsAdapter = actionAdapter

        rowsAdapter.add(row)
    }

    private fun setupRelatedMoviesRow() {
        // Load related movies from same category
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getCatalog(category = "phim-le", limit = 15)
                }

                response.movies?.let { relatedMovies ->
                    if (relatedMovies.isNotEmpty()) {
                        val cardPresenter = CardPresenter()
                        val listRowAdapter = ArrayObjectAdapter(cardPresenter)

                        relatedMovies
                            .filter { it.slug != movie.slug }
                            .take(10)
                            .forEach { listRowAdapter.add(it) }

                        if (listRowAdapter.size() > 0) {
                            val header = HeaderItem(getString(R.string.related_movies))
                            rowsAdapter.add(ListRow(header, listRowAdapter))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadBackgroundImage() {
        val imageUrl = movie.getThumbImage()
        val context = context ?: return
        
        if (imageUrl.isNotEmpty()) {
            Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        if (isAdded) {
                            backgroundController.coverBitmap = resource
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }

    private fun playMovie() {
        // Start playback - PlaybackFragment will fetch stream URL if needed
        val intent = Intent(requireContext(), PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_MOVIE, movie)
            // Stream URL will be fetched by PlaybackFragment
        }
        startActivity(intent)
    }

    private fun addToList() {
        val added = MyListManager.toggle(movie)
        val message = if (added) {
            "Added to My List"
        } else {
            "Removed from My List"
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun parseAnyToList(any: Any?): List<String>? {
        if (any == null) return null
        if (any is List<*>) {
            return any.filterIsInstance<String>()
        }
        if (any is String) {
            return any.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return null
    }
}
