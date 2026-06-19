package com.ablecredit.manualkotlinclient.ui.dashboard

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.ui.flow.FlowFragment
import com.ablecredit.manualkotlinclient.ui.home.HomeFragment
import com.ablecredit.manualkotlinclient.ui.profile.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class DashboardActivity : AppCompatActivity() {

    companion object {
        private const val TAG_HOME = "tag_home"
        private const val TAG_FLOW = "tag_flow"
        private const val TAG_PROFILE = "tag_profile"
    }

    private lateinit var homeFragment: HomeFragment
    private lateinit var flowFragment: FlowFragment
    private lateinit var profileFragment: ProfileFragment
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            flowFragment = FlowFragment()
            profileFragment = ProfileFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, profileFragment, TAG_PROFILE).hide(profileFragment)
                .add(R.id.fragment_container, flowFragment, TAG_FLOW).hide(flowFragment)
                .add(R.id.fragment_container, homeFragment, TAG_HOME)
                .commit()
            activeFragment = homeFragment
        } else {
            val fm = supportFragmentManager
            homeFragment = (fm.findFragmentByTag(TAG_HOME) as? HomeFragment) ?: HomeFragment()
            flowFragment = (fm.findFragmentByTag(TAG_FLOW) as? FlowFragment) ?: FlowFragment()
            profileFragment = (fm.findFragmentByTag(TAG_PROFILE) as? ProfileFragment) ?: ProfileFragment()
            activeFragment = fm.fragments.firstOrNull { !it.isHidden } ?: homeFragment
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { switchTo(homeFragment); true }
                R.id.nav_flow -> { switchTo(flowFragment); true }
                R.id.nav_profile -> { switchTo(profileFragment); true }
                else -> false
            }
        }
    }

    private fun switchTo(fragment: Fragment) {
        val current = activeFragment
        if (current == null || current == fragment) return
        supportFragmentManager.beginTransaction().hide(current).show(fragment).commit()
        activeFragment = fragment
    }
}
