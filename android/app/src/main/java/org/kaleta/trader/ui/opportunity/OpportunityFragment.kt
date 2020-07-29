package org.kaleta.trader.ui.opportunity

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
import org.kaleta.trader.data.CompanyAdapter

class OpportunityFragment : Fragment() {

    private lateinit var opportunityViewModel: OpportunityViewModel

    private val adapter = CompanyAdapter()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        opportunityViewModel =
            ViewModelProviders.of(this).get(OpportunityViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_opportunities, container, false)
        val recyclerView: RecyclerView = root.findViewById(R.id.opportunities)

        opportunityViewModel.text.observe(viewLifecycleOwner, Observer {
            recyclerView.layoutManager = LinearLayoutManager(root.context)
            recyclerView.adapter = adapter
        })

        return root
    }
}