package org.kaleta.trader.ui.logs

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
import org.kaleta.trader.adapter.LogAdapter

class LogsFragment : Fragment() {

    private lateinit var logsViewModel: LogsViewModel

    private val adapter = LogAdapter()


    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        logsViewModel =
                ViewModelProviders.of(this).get(LogsViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_logs, container, false)
        val recyclerView: RecyclerView = root.findViewById(R.id.logs)

        logsViewModel.text.observe(viewLifecycleOwner, Observer {
            recyclerView.layoutManager = LinearLayoutManager(root.context)
            recyclerView.adapter = adapter
        })

        return root
    }
}