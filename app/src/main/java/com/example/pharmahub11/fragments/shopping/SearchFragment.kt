package com.example.pharmahub11.fragments.shopping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.BestProductsAdapter
import com.example.pharmahub11.databinding.FragmentSearchBinding
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.util.hideKeyboard
import com.example.pharmahub11.util.showBottomNavigationView
import com.example.pharmahub11.viewmodel.SearchViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.fragment_search) {
    private lateinit var binding: FragmentSearchBinding
    private val viewModel by viewModels<SearchViewModel>()
    private val searchAdapter by lazy { BestProductsAdapter() }

    // Root view for Snackbar
    private lateinit var snackbarView: View
    private var currentQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchBinding.inflate(inflater, container, false)
        snackbarView = binding.root // Use the root view for Snackbar
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSearchView()
        setupRecyclerView()
        setupFilterChips()
        observeSearchResults()

        searchAdapter.onClick = { product ->
            hideKeyboard()
            val action = SearchFragmentDirections.actionSearchFragmentToProductDetailsFragment(product)
            findNavController().navigate(action)
        }
    }

    // Solution 1: Add programmatic styling in setupSearchView()
    private fun setupSearchView() {
        binding.searchView.isIconified = false
        binding.searchView.requestFocus()

        // Fix text visibility issues
        val searchEditText = binding.searchView.findViewById<androidx.appcompat.widget.SearchView.SearchAutoComplete>(
            androidx.appcompat.R.id.search_src_text
        )
        searchEditText?.apply {
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
            setHintTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            textSize = 16f
        }

        // Rest of your existing setupSearchView() code...
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    currentQuery = it.trim()
                    if (currentQuery.isNotEmpty()) {
                        viewModel.searchProducts(currentQuery)
                        hideKeyboard()
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { text ->
                    currentQuery = text.trim()
                    when {
                        currentQuery.isEmpty() -> {
                            searchAdapter.differ.submitList(emptyList())
                            binding.tvNoResults.visibility = View.GONE
                        }
                        currentQuery.length >= 2 -> {
                            viewModel.searchProducts(currentQuery)
                        }
                        else -> {
                            searchAdapter.differ.submitList(emptyList())
                            binding.tvNoResults.visibility = View.GONE
                        }
                    }
                }
                return true
            }
        })

        binding.searchView.setOnCloseListener {
            currentQuery = ""
            searchAdapter.differ.submitList(emptyList())
            binding.tvNoResults.visibility = View.GONE
            viewModel.clearSearchResults()
            false
        }
    }

    private fun setupRecyclerView() {
        binding.rvSearchResults.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = searchAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun setupFilterChips() {
        val categories = listOf(
            "All" to null,
            "Prescription" to "Prescription",
            "OTC" to "Over The Counter",
            "Vitamins" to "Vitamins",
            "First Aid" to "First Aid",
            "Wellness" to "Wellness" // Added Wellness category
        )

        categories.forEach { (displayName, categoryValue) ->
            val chip = Chip(requireContext()).apply {
                text = displayName
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        // Clear other chips when one is selected
                        clearOtherChips(this)

                        // Set the category filter
                        viewModel.setCategoryFilter(categoryValue)

                        // Re-search with current query if available
                        if (currentQuery.length >= 2) {
                            viewModel.searchProducts(currentQuery)
                        } else if (currentQuery.isEmpty()) {
                            // If no query, show all products in category or clear if "All"
                            if (categoryValue == null) {
                                searchAdapter.differ.submitList(emptyList())
                                binding.tvNoResults.visibility = View.GONE
                            } else {
                                viewModel.searchProducts("") // Empty search to get all products in category
                            }
                        }
                    }
                }
            }
            binding.filterChipGroup.addView(chip)
        }

        // Set "All" chip as checked by default
        (binding.filterChipGroup.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun clearOtherChips(selectedChip: Chip) {
        for (i in 0 until binding.filterChipGroup.childCount) {
            val chip = binding.filterChipGroup.getChildAt(i) as? Chip
            if (chip != selectedChip) {
                chip?.isChecked = false
            }
        }
    }

    private fun observeSearchResults() {
        viewModel.searchResults.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.searchProgressBar.visibility = View.VISIBLE
                    binding.tvNoResults.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.searchProgressBar.visibility = View.GONE
                    val products = resource.data ?: emptyList()
                    searchAdapter.differ.submitList(products)

                    // Show no results message based on search context
                    binding.tvNoResults.visibility = if (products.isEmpty()) {
                        // Only show "no results" if user actually searched for something
                        if (currentQuery.isNotEmpty() || getSelectedCategory() != null) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    } else {
                        View.GONE
                    }
                }
                is Resource.Error -> {
                    binding.searchProgressBar.visibility = View.GONE
                    binding.tvNoResults.visibility = View.VISIBLE
                    showError(resource.message ?: "Search failed. Please try again.")
                }
                else -> Unit
            }
        }
    }

    private fun getSelectedCategory(): String? {
        for (i in 0 until binding.filterChipGroup.childCount) {
            val chip = binding.filterChipGroup.getChildAt(i) as? Chip
            if (chip?.isChecked == true) {
                return when (chip.text.toString()) {
                    "All" -> null
                    "OTC" -> "Over The Counter"
                    else -> chip.text.toString()
                }
            }
        }
        return null
    }

    private fun showError(message: String) {
        // Use the root view for Snackbar to avoid NestedScrollView issues
        Snackbar.make(snackbarView, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                if (currentQuery.isNotEmpty()) {
                    viewModel.searchProducts(currentQuery)
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        showBottomNavigationView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear any ongoing searches
        viewModel.clearSearchResults()
    }
}