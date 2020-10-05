package org.kaleta.trader.ui.asset

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.kaleta.trader.R
import org.kaleta.trader.adapter.AssetAdapter

class AssetFragment : Fragment() {

    private lateinit var assetViewModel: AssetViewModel

    private val adapter = AssetAdapter()


    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        assetViewModel =
                ViewModelProviders.of(this).get(AssetViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_asset, container, false)
        val recyclerView: RecyclerView = root.findViewById(R.id.assets)
        assetViewModel.text.observe(viewLifecycleOwner, Observer {
            recyclerView.layoutManager = LinearLayoutManager(root.context)
            recyclerView.adapter = adapter
        })

        return root
    }
}