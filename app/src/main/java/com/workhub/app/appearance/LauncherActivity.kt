package com.workhub.app.appearance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.workhub.app.MainActivity

/** The launcher alias must not own the visible activity when its icon is switched. */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        ))
        finish()
    }
}
