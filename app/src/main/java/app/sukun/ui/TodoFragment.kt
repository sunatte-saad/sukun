package app.sukun.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.sukun.R
import app.sukun.data.Prefs
import app.sukun.data.TodoItem
import app.sukun.data.toTodoJson
import app.sukun.data.toTodoList
import app.sukun.databinding.FragmentTodoBinding
import app.sukun.databinding.ItemTodoBinding
import app.sukun.helper.showToast

class TodoFragment : Fragment() {

    private var _binding: FragmentTodoBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private val todos = mutableListOf<TodoItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTodoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        todos.clear()
        todos.addAll(prefs.todoItemsJson.toTodoList())

        refreshList()

        binding.tvAddTodo.setOnClickListener { addTodo() }
        binding.etTodoInput.setOnEditorActionListener { _, _, _ -> addTodo(); true }
        binding.tvClearCompleted.setOnClickListener { clearCompleted() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshList() {
        binding.todoList.removeAllViews()
        if (todos.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvClearCompleted.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            todos.forEach { todo ->
                val itemBinding = ItemTodoBinding.inflate(
                    layoutInflater, binding.todoList, true
                )
                itemBinding.tvTodoTitle.text = todo.text
                itemBinding.tvTodoTitle.alpha = if (todo.completed) 0.5f else 1f
                itemBinding.cbTodoCompleted.isChecked = todo.completed
                itemBinding.cbTodoCompleted.setOnCheckedChangeListener { _, checked ->
                    val index = todos.indexOfFirst { it.id == todo.id }
                    if (index >= 0) {
                        todos[index] = todo.copy(completed = checked)
                        saveTodos()
                        refreshList()
                    }
                }
                itemBinding.ivTodoDelete.setOnClickListener { deleteTodo(todo) }
            }
            binding.tvClearCompleted.visibility = if (todos.any { it.completed }) View.VISIBLE else View.GONE
        }
        scrollToBottom()
    }

    private fun scrollToBottom() {
        binding.todoScroll.post {
            binding.todoScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun addTodo() {
        val text = binding.etTodoInput.text?.toString()?.trim() ?: return
        if (text.isBlank()) return
        todos.add(0, TodoItem(id = System.currentTimeMillis(), text = text))
        saveTodos()
        binding.etTodoInput.text?.clear()
        refreshList()
        requireContext().showToast(R.string.todo_added)
    }

    private fun deleteTodo(todo: TodoItem) {
        todos.removeAll { it.id == todo.id }
        saveTodos()
        refreshList()
        requireContext().showToast(R.string.todo_deleted)
    }

    private fun clearCompleted() {
        todos.removeAll { it.completed }
        saveTodos()
        refreshList()
        requireContext().showToast(R.string.todo_cleared)
    }

    private fun saveTodos() {
        prefs.todoItemsJson = todos.toTodoJson()
    }
}
