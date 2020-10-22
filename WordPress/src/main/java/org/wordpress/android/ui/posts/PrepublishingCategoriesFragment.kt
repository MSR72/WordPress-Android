package org.wordpress.android.ui.posts

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.prepublishing_categories_fragment.*
import kotlinx.android.synthetic.main.prepublishing_toolbar.*
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.ui.pages.SnackbarMessageHolder
import org.wordpress.android.ui.posts.EditPostSettingsFragment.EditPostActivityHook
import org.wordpress.android.ui.posts.PrepublishingHomeItemUiState.ActionType.ADD_CATEGORY
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.util.ToastUtils
import org.wordpress.android.util.ToastUtils.Duration.SHORT
import javax.inject.Inject

class PrepublishingCategoriesFragment : Fragment(R.layout.prepublishing_categories_fragment) {
    private var closeListener: PrepublishingScreenClosedListener? = null
    private var actionListener: PrepublishingActionClickedListener? = null

    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: PrepublishingCategoriesViewModel
    @Inject lateinit var uiHelpers: UiHelpers

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as WordPress).component().inject(this)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        closeListener = parentFragment as PrepublishingScreenClosedListener
        actionListener = parentFragment as PrepublishingActionClickedListener
    }

    override fun onDetach() {
        super.onDetach()
        closeListener = null
        actionListener = null
    }

    override fun onResume() {
        // Note: This supports the re-calculation and visibility of views when coming from stories.
        val needsRequestLayout = requireArguments().getBoolean(NEEDS_REQUEST_LAYOUT)
        if (needsRequestLayout) {
            requireActivity().window.decorView.requestLayout()
        }
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initBackButton()
        initAddCategoryButton()
        initRecyclerView()
        initViewModel()
    }

    private fun initBackButton() {
        back_button.setOnClickListener {
            viewModel.onBackButtonClick()
        }
    }

    private fun initAddCategoryButton() {
        add_action_button.setOnClickListener {
            viewModel.onAddCategoryClick()
        }
    }

    private fun initRecyclerView() {
        recycler_view.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        recycler_view.adapter = PrepublishingCategoriesAdapter(uiHelpers)
        recycler_view.addItemDecoration(
                DividerItemDecoration(
                        recycler_view.context,
                        DividerItemDecoration.VERTICAL
                )
        )
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this, viewModelFactory)
                .get(PrepublishingCategoriesViewModel::class.java)
        startObserving()
    }

    private fun startObserving() {
        viewModel.navigateToHomeScreen.observe(viewLifecycleOwner, Observer { event ->
            event?.applyIfNotHandled {
                closeListener?.onBackClicked()
            }
        })

        viewModel.navigateToAddCategoryScreen.observe(viewLifecycleOwner, Observer { event ->
            event?.applyIfNotHandled {
                actionListener?.onActionClicked(ADD_CATEGORY)
            }
        })

        viewModel.toolbarTitleUiState.observe(viewLifecycleOwner, Observer { uiString ->
            toolbar_title.text = uiHelpers.getTextOfUiString(requireContext(), uiString)
        })

        viewModel.snackbarEvents.observe(viewLifecycleOwner, Observer { event ->
            event?.applyIfNotHandled {
                showToast()
            }
        })

        viewModel.uiState.observe(viewLifecycleOwner, Observer {
            (recycler_view.adapter as PrepublishingCategoriesAdapter).update(
                    it.categoriesListItemUiState
            )
            with(uiHelpers) {
                updateVisibility(add_action_button, it.addCategoryActionButtonVisibility)
            }
        })

        val siteModel = requireArguments().getSerializable(WordPress.SITE) as SiteModel
        viewModel.start(getEditPostRepository(), siteModel)
    }

    private fun getEditPostRepository(): EditPostRepository {
        val editPostActivityHook = requireNotNull(getEditPostActivityHook()) {
            "This is possibly null because it's " +
                    "called during config changes."
        }

        return editPostActivityHook.editPostRepository
    }

    private fun getEditPostActivityHook(): EditPostActivityHook? {
        val activity = activity ?: return null
        return if (activity is EditPostActivityHook) {
            activity
        } else {
            throw RuntimeException("$activity must implement EditPostActivityHook")
        }
    }

    private fun SnackbarMessageHolder.showToast() {
        val message = uiHelpers.getTextOfUiString(requireContext(), this.message).toString()
        ToastUtils.showToast(
                requireContext(), message,
                SHORT
        )
    }

    companion object {
        const val TAG = "prepublishing_categories_fragment_tag"
        const val NEEDS_REQUEST_LAYOUT = "prepublishing_categories_fragment_needs_request_layout"
        @JvmStatic fun newInstance(
            site: SiteModel,
            needsRequestLayout: Boolean
        ): PrepublishingCategoriesFragment {
            val bundle = Bundle().apply {
                putSerializable(WordPress.SITE, site)
                putBoolean(NEEDS_REQUEST_LAYOUT, needsRequestLayout)
            }
            return PrepublishingCategoriesFragment().apply { arguments = bundle }
        }
    }
}