package droidkaigi.github.io.challenge2019

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.RecyclerView
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import com.squareup.moshi.Types
import droidkaigi.github.io.challenge2019.data.db.ArticlePreferences
import droidkaigi.github.io.challenge2019.data.db.ArticlePreferences.Companion.saveArticleIds
import droidkaigi.github.io.challenge2019.data.repository.Resource
import droidkaigi.github.io.challenge2019.data.repository.entity.Story

class MainActivity : BaseActivity() {

    companion object {
        private const val STATE_STORIES = "stories"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressView: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var storyAdapter: StoryAdapter

    private val storyJsonAdapter = moshi.adapter(Story::class.java)
    private val storiesJsonAdapter =
        moshi.adapter<List<Story?>>(Types.newParameterizedType(List::class.java, Story::class.java))

    private val viewModel: MainViewModel by lazy {
        ViewModelProviders.of(this).get(MainViewModel::class.java)
    }

    override fun getContentView(): Int {
        return R.layout.activity_main
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recyclerView = findViewById(R.id.item_recycler)
        progressView = findViewById(R.id.progress)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh)

        setupRecyclerView()

        swipeRefreshLayout.setOnRefreshListener { loadTopStories() }

        val savedStories = savedInstanceState?.let { bundle ->
            bundle.getString(STATE_STORIES)?.let { storiesJson ->
                storiesJsonAdapter.fromJson(storiesJson)
            }
        }

        if (savedStories != null) {
            storyAdapter.stories = savedStories.toMutableList()
            storyAdapter.alreadyReadStories = ArticlePreferences.getArticleIds(this@MainActivity)
            storyAdapter.notifyDataSetChanged()
            return
        }

        viewModel.topStories.observe(this, Observer { resource ->
            when (resource) {
                is Resource.Success -> {
                    progressView.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                    storyAdapter.stories = resource.data.toMutableList()
                    storyAdapter.alreadyReadStories = ArticlePreferences.getArticleIds(this@MainActivity)
                    storyAdapter.notifyDataSetChanged()
                }
                is Resource.Error -> {
                    showError(resource.t)
                }
            }
        })

        viewModel.story.observe(this, Observer { resource ->
            when (resource) {
                is Resource.Success -> {
                    val story = resource.data
                    val index = storyAdapter.stories.indexOf(story)
                    if (index == -1) return@Observer

                    storyAdapter.stories[index] = story
                    runOnUiThread {
                        storyAdapter.alreadyReadStories = ArticlePreferences.getArticleIds(this@MainActivity)
                        storyAdapter.notifyItemChanged(index)
                    }
                }
                is Resource.Error -> {
                    showError(resource.t)
                }
            }
        })

        loadTopStories(true)
    }

    private fun setupRecyclerView() {
        val itemDecoration = DividerItemDecoration(recyclerView.context, DividerItemDecoration.VERTICAL)
        recyclerView.addItemDecoration(itemDecoration)
        storyAdapter = StoryAdapter(
            stories = mutableListOf(),
            onClickStory = { story ->
                val storyJson = storyJsonAdapter.toJson(story)
                val intent = Intent(this@MainActivity, StoryActivity::class.java).apply {
                    putExtra(StoryActivity.EXTRA_ITEM_JSON, storyJson)
                }
                startActivity(intent)
            },
            onClickMenuItem = { story, menuItemId ->
                when (menuItemId) {
                    R.id.copy_url -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip = ClipData.newPlainText("url", story.url)
                    }
                    R.id.refresh -> {
                        viewModel.loadStory(story.id)
                    }
                }
            },
            alreadyReadStories = ArticlePreferences.getArticleIds(this)
        )
        recyclerView.adapter = storyAdapter
    }

    private fun loadTopStories(showProgressView: Boolean = false) {
        if (showProgressView) progressView.visibility = Util.setVisibility(true)
        viewModel.loadTopStories()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when(resultCode) {
            Activity.RESULT_OK -> {
                data?.getLongExtra(StoryActivity.READ_ARTICLE_ID, 0L)?.let { id ->
                    if (id != 0L) {
                        saveArticleIds(this, id.toString())
                        storyAdapter.alreadyReadStories = ArticlePreferences.getArticleIds(this)
                        storyAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.refresh -> {
                progressView.visibility = Util.setVisibility(true)
                loadTopStories()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.apply {
            putString(STATE_STORIES, storiesJsonAdapter.toJson(storyAdapter.stories))
        }

        super.onSaveInstanceState(outState)
    }
}
