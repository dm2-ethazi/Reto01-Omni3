package com.omni3.gestorhogwartsmovil

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.omni3.gestorhogwartsmovil.databinding.ActivityStudentViewBinding

class StudentViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}