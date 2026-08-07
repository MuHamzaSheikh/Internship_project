package com.example.businesscardscanner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.businesscardscanner.adapters.BusinessCardAdapter
import com.example.businesscardscanner.databinding.FragmentCardListBinding
import com.example.businesscardscanner.dialogs.DeleteCardDialog
import com.example.businesscardscanner.repository.BusinessCardRepository
import com.example.businesscardscanner.utils.CardActionUtils
import com.example.businesscardscanner.utils.CardListSearchUtils
import kotlinx.coroutines.launch

class ColleagueFragment : Fragment() {

    private var _binding: FragmentCardListBinding? = null
    private val binding get() = _binding!!

    private var currentQuery: String = ""
    private var allCards: List<com.example.businesscardscanner.models.BusinessCard> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCardListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repository = BusinessCardRepository.getInstance(requireContext().applicationContext)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                repository.observeActiveCards().collect { cards ->
                    val scoped = cards.filter { it.group.equals("Colleague", true) }
                    allCards = scoped
                    val filtered = CardListSearchUtils.filter(scoped, currentQuery)
                    binding.recyclerView.adapter = BusinessCardAdapter(
                        filtered,
                        onCardClick = { startActivity(CardPreviewActivity.createIntent(requireContext(), it.id)) },
                        onEdit = { startActivity(CardWorkflowActivity.createIntent(requireContext(), CardWorkflowActivity.StartStep.CARD_EDIT, it.id)) },
                        onShare = { startActivity(Intent.createChooser(CardActionUtils.shareCard(requireContext(), it), "Share Card")) },
                        onSave = { card ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                val ok = CardActionUtils.saveCardImageToGallery(requireContext(), card)
                                Toast.makeText(requireContext(), if (ok) "Saved to Gallery" else "Unable to save to Gallery", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { card ->
                            DeleteCardDialog(
                                requireContext(),
                                onCancel = {},
                                onDelete = {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        runCatching {
                                            Log.d(TAG, "Move to recycle bin start id=${card.id}")
                                            repository.moveToRecycleBin(card)
                                            Log.d(TAG, "Move to recycle bin success id=${card.id}")
                                            val updated = repository.getById(card.id)
                                            Log.d(TAG, "Post-delete lookup id=${card.id} present=${updated != null} recycleBin=${updated?.isInRecycleBin}")
                                            Log.d(TAG, "Navigating to RecycleBinActivity id=${card.id}")
                                            startActivity(Intent(requireContext(), RecycleBinActivity::class.java))
                                        }.onFailure { error ->
                                            Log.e(TAG, "Move to recycle bin failed id=${card.id}", error)
                                            Toast.makeText(requireContext(), "Unable to delete card", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ).show()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateSearch(query: String) {
        currentQuery = query
        if (_binding != null) {
            (binding.recyclerView.adapter as? BusinessCardAdapter)?.let { adapter ->
                val filtered = CardListSearchUtils.filter(allCards, currentQuery)
                adapter.updateItems(filtered)
            }
        }
    }

    companion object {
        private const val TAG = "CardScannerDelete"

        @JvmStatic
        fun newInstance() = ColleagueFragment()
    }
}
