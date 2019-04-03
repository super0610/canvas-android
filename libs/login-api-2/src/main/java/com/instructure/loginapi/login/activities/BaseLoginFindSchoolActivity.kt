/*
 * Copyright (C) 2017 - present Instructure, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.instructure.loginapi.login.activities

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.instructure.canvasapi2.StatusCallback
import com.instructure.canvasapi2.managers.AccountDomainManager
import com.instructure.canvasapi2.models.AccountDomain
import com.instructure.canvasapi2.utils.ApiType
import com.instructure.canvasapi2.utils.LinkHeaders
import com.instructure.loginapi.login.R
import com.instructure.loginapi.login.adapter.DomainAdapter
import com.instructure.loginapi.login.dialog.ErrorReportDialog
import com.instructure.loginapi.login.util.Const
import com.instructure.pandautils.utils.ColorUtils
import com.instructure.pandautils.utils.ViewStyler
import kotlinx.android.synthetic.main.activity_find_school.*
import retrofit2.Response

abstract class BaseLoginFindSchoolActivity : AppCompatActivity(), ErrorReportDialog.ErrorReportDialogResultListener {

    private var mDomainAdapter: DomainAdapter? = null
    private var mNextActionButton: TextView? = null
    private val mDelayFetchAccountHandler = Handler()
    protected var mWhatsYourSchoolName: TextView? = null
    private var mLoginFlowLogout: TextView? = null

    /**
     * Worker thread for fetching account domains.
     */
    private val mFetchAccountsWorker = Runnable {
        if (domainInput != null) {
            val query = domainInput!!.text.toString()
            AccountDomainManager.searchAccounts(query, mAccountDomainCallback)
        }
    }

    private val mAccountDomainCallback = object : StatusCallback<List<AccountDomain>>() {

        override fun onResponse(response: Response<List<AccountDomain>>, linkHeaders: LinkHeaders, type: ApiType) {
            if (type.isCache) return

            val domains = response.body()?.toMutableList()

            val isDebuggable = 0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE

            if (isDebuggable) {
                // Put these domains first
                domains?.add(0, createAccountForDebugging("mobiledev.instructure.com"))
                domains?.add(1, createAccountForDebugging("mobiledev.beta.instructure.com"))
                domains?.add(2, createAccountForDebugging("mobileqa.instructure.com"))
                domains?.add(3, createAccountForDebugging("mobileqat.instructure.com"))
                domains?.add(4, createAccountForDebugging("clare.instructure.com"))
                domains?.add(5, createAccountForDebugging("mobileqa.test.instructure.com"))
            }

            if (mDomainAdapter != null) {
                mDomainAdapter!!.setItems(domains)
                mDomainAdapter!!.filter.filter(domainInput!!.text.toString())
            }
        }
    }

    @ColorInt
    protected abstract fun themeColor(): Int

    protected abstract fun signInActivityIntent(accountDomain: AccountDomain): Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_school)
        bindViews()
        applyTheme()
    }

    private fun bindViews() {
        mWhatsYourSchoolName = findViewById(R.id.whatsYourSchoolName)
        mLoginFlowLogout = findViewById(R.id.loginFlowLogout)
        toolbar.apply {
            navigationIcon = ContextCompat.getDrawable(this@BaseLoginFindSchoolActivity, R.drawable.ic_action_arrow_back)
            navigationIcon?.isAutoMirrored = true
            setNavigationContentDescription(R.string.close)
            inflateMenu(R.menu.menu_next)
            setOnMenuItemClickListener(Toolbar.OnMenuItemClickListener { item ->
                if (item.itemId == R.id.next) {
                    validateDomain(AccountDomain(domainInput.text.toString()))
                    return@OnMenuItemClickListener true
                }
                false
            })
            setNavigationOnClickListener { finish() }
        }

        val a11yManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (a11yManager != null && (a11yManager.isEnabled || a11yManager.isTouchExplorationEnabled)) {
            toolbar.isFocusable = true
            toolbar.isFocusableInTouchMode = true
            toolbar.postDelayed({
                toolbar.requestFocus()
                toolbar.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
            }, 500)
        }

        mNextActionButton = findViewById(R.id.next)
        mNextActionButton!!.isEnabled = false
        mNextActionButton!!.setTextColor(ContextCompat.getColor(this@BaseLoginFindSchoolActivity, R.color.login_grayCanvasLogo))

        domainInput.setOnEditorActionListener { _, _, _ ->
            validateDomain(AccountDomain(domainInput!!.text.toString()))
            true
        }

        domainInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                if (mDomainAdapter != null) {
                    mDomainAdapter!!.filter.filter(s)
                    fetchAccountDomains()
                }

                if (mNextActionButton != null) {
                    if (TextUtils.isEmpty(s.toString())) {
                        mNextActionButton!!.isEnabled = false
                        mNextActionButton!!.setTextColor(ContextCompat.getColor(
                                this@BaseLoginFindSchoolActivity, R.color.login_grayCanvasLogo))
                    } else {
                        mNextActionButton!!.isEnabled = true
                        mNextActionButton!!.setTextColor(ContextCompat.getColor(
                                this@BaseLoginFindSchoolActivity, R.color.login_loginFlowBlue))
                    }
                }
            }
        })

        mDomainAdapter = DomainAdapter(object : DomainAdapter.DomainEvents {
            override fun onDomainClick(account: AccountDomain) {
                domainInput.setText(account.domain)
                domainInput.setSelection(domainInput.text.length)
                validateDomain(account)
            }

            override fun onHelpClick() {
                val webHelpIntent = Intent(Intent.ACTION_VIEW, Uri.parse(Const.FIND_SCHOOL_HELP_URL))
                startActivity(webHelpIntent)
            }
        })

        val recyclerView = findViewById<RecyclerView>(R.id.findSchoolRecyclerView)
        recyclerView.addItemDecoration(DividerItemDecoration(this, RecyclerView.VERTICAL))
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        recyclerView.adapter = mDomainAdapter
    }

    /**
     * Override to handle a logout click event. Use the showLogout() method to enable the buttons visibility
     */
    protected fun logout() {}

    /**
     * Shows a logout button. Calls from click return to a logout()
     * @param show a value to indicate if the logout button should be shown.
     */
    protected fun showLogout(show: Boolean) {
        mLoginFlowLogout!!.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            mLoginFlowLogout!!.setOnClickListener { logout() }
        }
    }

    private fun validateDomain(accountDomain: AccountDomain) {
        var url: String? = accountDomain.domain!!.toLowerCase().replace(" ", "")

        //if the user enters nothing, try to connect to canvas.instructure.com
        if (url!!.trim { it <= ' ' }.isEmpty()) {
            url = "canvas.instructure.com"
        }

        //if there are no periods, append .instructure.com
        if (!url.contains(".") || url.endsWith(".beta")) {
            url += ".instructure.com"
        }

        //URIs need to to start with a scheme.
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }

        //Get just the host.
        val uri = Uri.parse(url)
        url = uri.host

        //Strip off www. if they typed it.
        if (url!!.startsWith("www.")) {
            url = url.substring(4)
        }

        accountDomain.domain = url

        val intent = signInActivityIntent(accountDomain)
        intent.putExtra(Const.CANVAS_LOGIN, getIntent().extras!!.getInt(Const.CANVAS_LOGIN, 0))
        startActivity(intent)
    }

    protected open fun applyTheme() {
        val color = themeColor()

        val view = LayoutInflater.from(this).inflate(R.layout.login_toolbar_icon, null, false)
        val icon = view.findViewById<ImageView>(R.id.loginLogo)
        icon.setImageDrawable(ColorUtils.colorIt(color, icon.drawable))

        toolbar!!.addView(view)

        ViewStyler.setStatusBarLight(this)
    }

    /**
     * Handles fetching account domains. Uses a worker runnable and handler to cancel fetching too often.
     */
    private fun fetchAccountDomains() {
        mDelayFetchAccountHandler.removeCallbacks(mFetchAccountsWorker)
        mDelayFetchAccountHandler.postDelayed(mFetchAccountsWorker, 500)
    }

    private fun createAccountForDebugging(domain: String): AccountDomain {
        val account = AccountDomain()
        account.domain = domain
        account.name = "@ $domain"
        account.distance = 0.0
        return account
    }

    override fun onDestroy() {
        if (mDelayFetchAccountHandler != null && mFetchAccountsWorker != null) {
            mDelayFetchAccountHandler.removeCallbacks(mFetchAccountsWorker)
        }
        super.onDestroy()
    }

    //region Help & Support

    override fun onTicketPost() {
        Toast.makeText(this, R.string.errorReportThankyou, Toast.LENGTH_LONG).show()
    }

    override fun onTicketError() {}

    companion object {
        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }
}