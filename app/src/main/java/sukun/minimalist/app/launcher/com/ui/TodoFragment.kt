package sukun.minimalist.app.launcher.com.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.data.TodoItem
import sukun.minimalist.app.launcher.com.data.toTodoJson
import sukun.minimalist.app.launcher.com.data.toTodoList
import sukun.minimalist.app.launcher.com.databinding.FragmentTodoBinding
import sukun.minimalist.app.launcher.com.databinding.ItemTodoBinding
import sukun.minimalist.app.launcher.com.helper.showToast

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
                itemBinding.root.setOnLongClickListener { editTodo(todo); true }
                itemBinding.tvTodoTitle.setOnLongClickListener { editTodo(todo); true }
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

    private fun editTodo(todo: TodoItem) {
        val input = EditText(requireContext()).apply {
            setText(todo.text)
            setSelection(todo.text.length)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.todo_edit)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isBlank()) return@setPositiveButton
                val index = todos.indexOfFirst { it.id == todo.id }
                if (index >= 0) {
                    todos[index] = todo.copy(text = text)
                    saveTodos()
                    refreshList()
                    requireContext().showToast(R.string.todo_updated)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
