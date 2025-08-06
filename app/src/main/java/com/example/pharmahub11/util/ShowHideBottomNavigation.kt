package com.example.pharmahub11.util

import androidx.fragment.app.Fragment
import com.example.pharmahub11.R
import com.example.pharmahub11.activities.ShoppingActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

fun Fragment.hideBottomNavigationView(){
    val bottomNavigationView =
        (activity as ShoppingActivity).findViewById<BottomNavigationView>(
            com.example.pharmahub11.R.id.bottomNavigation
        )
    bottomNavigationView.visibility = android.view.View.GONE
}

fun Fragment.showBottomNavigationView(){
    val bottomNavigationView =
        (activity as ShoppingActivity).findViewById<BottomNavigationView>(
            com.example.pharmahub11.R.id.bottomNavigation
        )
    bottomNavigationView.visibility = android.view.View.VISIBLE
}