/*
 * Copyright 2020 R3BL LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.r3bl.stayawake

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.Log.d
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import com.r3bl.stayawake.MyTileService.Companion.TAG
import com.r3bl.stayawake.MyTileServiceSettings.changeSettings
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.TimeUnit

/** More info: https://stackoverflow.com/a/25510848/2085356 */
private class MySpinnerAdapter(context: Context, resource: Int, items: List<String>, private val font: Typeface) :
  ArrayAdapter<String>(context, resource, items) {
  // Affects default (closed) state of the spinner.
  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
      (super.getView(position, convertView, parent) as TextView).apply { typeface = font }

  // Affects opened state of the spinner.
  override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
      (super.getDropDownView(position, convertView, parent) as TextView).apply { typeface = font }
}

class MainActivity : AppCompatActivity() {
  private lateinit var typeNotoSansRegular: Typeface
  private lateinit var typeNotoSansBold: Typeface
  private lateinit var typeTitilumWebLight: Typeface
  private lateinit var typeTitilumWebRegular: Typeface
  private lateinit var mySettings: MyTileServiceSettings.ThreadSafeSettingsWrapper

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    mySettings = MyTileServiceSettings.ThreadSafeSettingsWrapper(this)

    //showAppIconInActionBar();
    //hideStatusBar();

    updatePaddingTopWithStatusBarHeight()

    loadAndApplyFonts()
    formatMessages()

    //handleAutoStartOfService()

    setupCheckbox()
    setupSpinner(typeNotoSansRegular)
  }

  override fun onStart() {
    super.onStart()
    MyTileServiceSettings.registerWithEventBus(this)
  }

  override fun onStop() {
    MyTileServiceSettings.unregisterFromEventBus(this)
    super.onStop()
  }

  /** Handle [SettingsChangedEvent] from [EventBus]. */
  @Subscribe(threadMode = ThreadMode.BACKGROUND)
  fun onSettingsChangedEvent(event: MyTileServiceSettings.SettingsChangedEvent) = event.settings.apply {
    mySettings.value = this
    runOnUiThread { formatMessages() }
    d(TAG, "MainActivity.onSettingsChangedEvent: ${mySettings}")
  }

//  private fun handleAutoStartOfService() {
//    if (mySettings.value.autoStartEnabled && MyTileService.isCharging(this)) {
//      MyTileService.fireIntentWithStartService(this)
//      d(TAG, "MainActivity.handleAutoStartOfService: Initiate auto start")
//    }
//    else d(TAG, "MainActivity.handleAutoStartOfService: Do not auto start")
//  }

  private fun setupCheckbox() = mySettings.value.autoStartEnabled.let { autoStartEnabled ->
    checkbox_prefs_auto_start.isChecked = autoStartEnabled
    d(TAG, "setupCheckbox: set checkbox state to: $autoStartEnabled")
  }

  private fun loadAndApplyFonts() {
    typeNotoSansRegular = Typeface.createFromAsset(assets, "fonts/notosans_regular.ttf")
    typeNotoSansBold = Typeface.createFromAsset(assets, "fonts/notosans_bold.ttf")
    typeTitilumWebLight = Typeface.createFromAsset(assets, "fonts/titilliumweb_light.ttf")
    typeTitilumWebRegular = Typeface.createFromAsset(assets, "fonts/titilliumweb_regular.ttf")

    listOf<TextView>(text_app_title).forEach { it.typeface = typeNotoSansBold }

    listOf<TextView>(text_marketing_message).forEach { it.typeface = typeTitilumWebLight }

    listOf<TextView>(text_introduction_heading, text_installation_heading, text_opensource_title).forEach {
      it.typeface = typeTitilumWebRegular
    }

    listOf<TextView>(text_spinner_timeout_description,
                     button_start_awake,
                     checkbox_prefs_auto_start,
                     text_introduction_content,
                     text_install_body,
                     text_install_body_1,
                     text_install_body_2,
                     text_install_body_3,
                     text_opensource_body).forEach {
      it.typeface = typeNotoSansRegular
    }
  }

  private fun setupSpinner(font: Typeface) = with(spinner_timeout) {
    // Create custom adatper to handle font.
    val myAdapter = MySpinnerAdapter(this@MainActivity,
                                     android.R.layout.simple_spinner_item,
                                     resources.getStringArray(R.array.spinner_timeout_choices).toList(),
                                     font)
    adapter = myAdapter

    // Restore saved selection.
    val savedTimeoutInSec: Long = mySettings.value.timeoutNotChargingSec
    val savedTimeoutInMin: Long = TimeUnit.MINUTES.convert(savedTimeoutInSec, TimeUnit.SECONDS)
    val position = myAdapter.getPosition(savedTimeoutInMin.toString())
    if (position != -1) {
      spinner_timeout.setSelection(position)
      formatMessages()
      d(TAG, "setupSpinner: set spinner selection to position: $position")
    }

    // Attach listeners to handle user selection.
    spinner_timeout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) {}

      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val selectionInMin: String = parent?.getItemAtPosition(position).toString()
        val selectionInSec: Long = TimeUnit.SECONDS.convert(selectionInMin.toLong(), TimeUnit.MINUTES)
        changeSettings(this@MainActivity) {
          timeoutNotChargingSec = selectionInSec
        }
        formatMessages()
        d(TAG, "onItemSelected: spinner selection is $selectionInMin")
      }
    }
  }

  /**
   * Updates app_title top padding, so that the title is displayed in a nice position relative to the status bar.
   * [More info](https://stackoverflow.com/a/3410200/2085356).
   */
  private fun updatePaddingTopWithStatusBarHeight() {
    val statusBarHeightId = resources.getIdentifier("status_bar_height", "dimen", "android")
    val statusBarHeightPx = when {
      statusBarHeightId > 0 -> resources.getDimensionPixelSize(statusBarHeightId)
      else                  -> resources.getDimensionPixelSize(R.dimen.status_bar_top_padding)
    }
    app_title.apply { setPadding(paddingLeft, statusBarHeightPx, paddingRight, paddingBottom) }
  }

  private fun hideStatusBar() {
    val decorView = window.decorView
    // Hide the status bar.
    decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
    // Remember that you should never show the action bar if the status bar is hidden, so hide that too if necessary.
    actionBar?.hide()
  }

  private fun showAppIconInActionBar() {
    supportActionBar?.apply {
      setDisplayShowHomeEnabled(true)
      setLogo(R.mipmap.ic_launcher)
      setDisplayUseLogoEnabled(true)
    }
  }

  /**
   * More info on clickable HTML:
   * - https://stackoverflow.com/a/38272292/2085356
   * - https://stackoverflow.com/a/42811913/2085356
   */
  @MainThread
  private fun formatMessages() {
    // Add actual minutes to string template.
    val hours = TimeUnit.SECONDS.toMinutes(mySettings.value.timeoutNotChargingSec)
    text_introduction_content.text = getString(R.string.introduction_body, hours)

    // Load the Open Source footer as HTML that can be clicked.
    text_opensource_body.movementMethod = LinkMovementMethod.getInstance()
    text_opensource_body.text = Html.fromHtml(getString(R.string.github_body), Html.FROM_HTML_MODE_COMPACT)

    // Spanning color on textviews.
    applySpan(text_install_body_1, R.string.install_body_1, "Step 1")
    applySpan(text_install_body_2, R.string.install_body_2, "Step 2")
    applySpan(text_install_body_3, R.string.install_body_3, "Step 3")
  }

  private fun applySpan(textView: TextView, stringResId: Int, substring: String) {
    setColorSpanOnTextView(textView,
                           getString(stringResId, substring),
                           substring,
                           getColor(R.color.colorTextDark))
  }

  private fun setColorSpanOnTextView(view: TextView,
                                     fulltext: String,
                                     subtext: String,
                                     color: Int
  ) {
    view.setText(fulltext, TextView.BufferType.SPANNABLE)
    val spannableText: Spannable = view.text as Spannable
    val subtextStartIndex: Int = fulltext.indexOf(subtext)
    spannableText.setSpan(ForegroundColorSpan(color),
                          subtextStartIndex,
                          subtextStartIndex + subtext.length,
                          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
  }

  fun buttonClicked(ignore: View) = MyTileService.fireIntentWithStartService(this)

  fun checkboxClicked(view: View) =
      changeSettings(this) {
        autoStartEnabled = (view as CheckBox).isChecked
      }
}