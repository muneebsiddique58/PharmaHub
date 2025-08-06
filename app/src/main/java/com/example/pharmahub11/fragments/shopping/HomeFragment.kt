package com.example.pharmahub11.fragments.shopping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.HomeViewpagerAdapter
import com.example.pharmahub11.databinding.FragmentHomeBinding
import com.example.pharmahub11.fragments.categories.*
import com.google.android.material.tabs.TabLayoutMediator

class HomeFragment : Fragment(R.layout.fragment_home) {
    private lateinit var binding: FragmentHomeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoriesFragments = arrayListOf(
            MainCategoryFragment(),
            OTCFragment(),
            WellnessFragment(),
            FirstAidFragment(),
            PrescriptionBasedFragment(),
            VitaminsFragment()
        )

        binding.viewpagerHome.isUserInputEnabled = false

        val viewPager2Adapter =
            HomeViewpagerAdapter(categoriesFragments, childFragmentManager, lifecycle)
        binding.viewpagerHome.adapter = viewPager2Adapter
        TabLayoutMediator(binding.tabLayout, binding.viewpagerHome) { tab, position ->
            when (position) {
                0 -> tab.text = "Main"
                1 -> tab.text = "Over-The-Counter"
                2 -> tab.text = "Wellness"
                3 -> tab.text = "FirstAid"
                4 -> tab.text = "Prescription"
                5 -> tab.text = "Vitamins"
            }
        }.attach()

        // Set click listener for search bar
        binding.searchBar.setOnClickListener {
            navigateToSearchFragment()
        }
        binding.locationIcon.setOnClickListener {
            navigateToLocationFragment()
        }
    }

    private fun navigateToSearchFragment() {
        // Using Navigation Component
        findNavController().navigate(R.id.action_homeFragment_to_searchFragment)

        // OR if you're not using Navigation Component:
        // val searchFragment = SearchFragment()
        // requireActivity().supportFragmentManager.beginTransaction()
        //     .replace(R.id.fragment_container, searchFragment)
        //     .addToBackStack(null)
        //     .commit()
    }
    private fun navigateToLocationFragment() {
        findNavController().navigate(R.id.action_homeFragment_to_locationFragment)
    }
}