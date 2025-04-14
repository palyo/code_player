package core.rocket.player.activities

import android.content.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.*
import coder.apps.space.library.base.*
import core.rocket.player.databinding.*

class HomeActivity : BaseActivity<ActivityHomeBinding>(ActivityHomeBinding::inflate) {

    val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            playFile(uri.toString())
        }
    }

    override fun ActivityHomeBinding.initExtra() {
        actionPick.setOnClickListener {
            documentPicker.launch(arrayOf("*/*"))
        }
    }

    fun playFile(filepath: String?) {
        val i = Intent(Intent.ACTION_VIEW, filepath?.toUri())
        i.setClass(this@HomeActivity, PlayerActivity::class.java)
        startActivity(i)
    }

    override fun ActivityHomeBinding.initListeners() {

    }

    override fun ActivityHomeBinding.initView() {}
}