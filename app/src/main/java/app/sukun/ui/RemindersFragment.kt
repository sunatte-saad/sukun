package app.sukun.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.sukun.R
import app.sukun.data.Prefs
import app.sukun.data.Reminder
import app.sukun.data.scheduleDescription
import app.sukun.data.toJsonString
import app.sukun.data.toReminderList
import app.sukun.databinding.DialogAddReminderBinding
import app.sukun.databinding.FragmentRemindersBinding
import app.sukun.databinding.ItemReminderBinding
import app.sukun.helper.ReminderScheduler
import app.sukun.helper.showToast
import java.util.Calendar

class RemindersFragment : Fragment() {

    private var _binding: FragmentRemindersBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private val reminders = mutableListOf<Reminder>()

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) requireContext().showToast(R.string.reminder_notifications_needed)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRemindersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        reminders.clear()
        reminders.addAll(prefs.remindersJson.toReminderList())

        requestNotificationsPermissionIfNeeded()
        refreshList()

        binding.tvAddReminder.setOnClickListener { showReminderDialog(null) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun requestNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun refreshList() {
        binding.remindersList.removeAllViews()
        if (reminders.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.tvEmpty.visibility = View.GONE
            reminders.forEach { reminder ->
                val itemBinding = ItemReminderBinding.inflate(
                    layoutInflater, binding.remindersList, true
                )
                itemBinding.tvReminderTitle.text = reminder.title
                itemBinding.tvReminderSchedule.text = reminder.scheduleDescription()
                itemBinding.tvReminderEnabled.text =
                    if (reminder.enabled) getString(R.string.on) else getString(R.string.off)
                itemBinding.tvReminderEnabled.alpha = if (reminder.enabled) 1f else 0.4f
                if (reminder.fireCount > 0) {
                    itemBinding.tvReminderStats.visibility = View.VISIBLE
                    val pct = (reminder.doneCount * 100) / reminder.fireCount
                    itemBinding.tvReminderStats.text =
                        getString(R.string.reminder_stats, reminder.doneCount, reminder.fireCount, pct)
                } else {
                    itemBinding.tvReminderStats.visibility = View.GONE
                }
                itemBinding.root.setOnClickListener { showReminderDialog(reminder) }
                itemBinding.ivReminderDelete.setOnClickListener { showDeleteConfirmation(reminder) }
                itemBinding.tvReminderEnabled.setOnClickListener { toggleReminder(reminder) }
            }
        }
    }

    private fun toggleReminder(reminder: Reminder) {
        val updated = reminder.copy(enabled = !reminder.enabled)
        updateReminder(updated)
        if (updated.enabled) {
            ReminderScheduler.schedule(requireContext(), updated)
        } else {
            ReminderScheduler.cancel(requireContext(), updated.id)
        }
        refreshList()
    }

    private fun showDeleteConfirmation(reminder: Reminder) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.delete_reminder_confirm, reminder.title))
            .setPositiveButton(R.string.reminder_delete) { _, _ -> deleteReminder(reminder) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteReminder(reminder: Reminder) {
        reminders.removeAll { it.id == reminder.id }
        saveAll()
        ReminderScheduler.cancel(requireContext(), reminder.id)
        refreshList()
        requireContext().showToast(R.string.reminder_deleted)
    }

    private fun showReminderDialog(existing: Reminder?) {
        val isEdit = existing != null
        val dialogBinding = DialogAddReminderBinding.inflate(layoutInflater)

        var selectedType = existing?.type ?: Reminder.Type.DAILY
        var selectedHour = existing?.hour ?: 9
        var selectedMinute = existing?.minute ?: 0
        var selectedInterval = existing?.intervalHours ?: 1
        val selectedDays = existing?.days?.toMutableSet() ?: mutableSetOf()

        dialogBinding.etReminderTitle.setText(existing?.title ?: "")
        dialogBinding.etReminderMessage.setText(existing?.message ?: "")

        fun updateTypeUI() {
            val buttons = listOf(
                dialogBinding.typeDailyBtn to Reminder.Type.DAILY,
                dialogBinding.typeHourlyBtn to Reminder.Type.HOURLY,
                dialogBinding.typeWeeklyBtn to Reminder.Type.WEEKLY
            )
            buttons.forEach { (btn, type) ->
                btn.alpha = if (type == selectedType) 1f else 0.35f
            }
            dialogBinding.intervalRow.visibility =
                if (selectedType == Reminder.Type.HOURLY) View.VISIBLE else View.GONE
            dialogBinding.daysRow.visibility =
                if (selectedType == Reminder.Type.WEEKLY) View.VISIBLE else View.GONE
        }

        fun updateTimeUI() {
            dialogBinding.tvSelectedTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        }

        fun updateIntervalUI() {
            dialogBinding.tvSelectedInterval.text =
                if (selectedInterval == 1) getString(R.string.reminder_interval_1h)
                else getString(R.string.reminder_interval_nh, selectedInterval)
        }

        fun updateDaysUI() {
            val dayButtons = listOf(
                dialogBinding.dayBtnSun to Calendar.SUNDAY,
                dialogBinding.dayBtnMon to Calendar.MONDAY,
                dialogBinding.dayBtnTue to Calendar.TUESDAY,
                dialogBinding.dayBtnWed to Calendar.WEDNESDAY,
                dialogBinding.dayBtnThu to Calendar.THURSDAY,
                dialogBinding.dayBtnFri to Calendar.FRIDAY,
                dialogBinding.dayBtnSat to Calendar.SATURDAY
            )
            dayButtons.forEach { (btn, day) ->
                btn.alpha = if (day in selectedDays) 1f else 0.35f
            }
        }

        updateTypeUI()
        updateTimeUI()
        updateIntervalUI()
        updateDaysUI()

        dialogBinding.typeDailyBtn.setOnClickListener { selectedType = Reminder.Type.DAILY; updateTypeUI() }
        dialogBinding.typeHourlyBtn.setOnClickListener { selectedType = Reminder.Type.HOURLY; updateTypeUI() }
        dialogBinding.typeWeeklyBtn.setOnClickListener { selectedType = Reminder.Type.WEEKLY; updateTypeUI() }

        dialogBinding.tvSelectedTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m ->
                selectedHour = h; selectedMinute = m; updateTimeUI()
            }, selectedHour, selectedMinute, true).show()
        }

        dialogBinding.tvSelectedInterval.setOnClickListener {
            val labels = arrayOf("1 hour", "2 hours", "3 hours", "4 hours", "6 hours", "8 hours", "12 hours")
            val values = intArrayOf(1, 2, 3, 4, 6, 8, 12)
            AlertDialog.Builder(requireContext())
                .setItems(labels) { _, i -> selectedInterval = values[i]; updateIntervalUI() }
                .show()
        }

        listOf(
            dialogBinding.dayBtnSun to Calendar.SUNDAY,
            dialogBinding.dayBtnMon to Calendar.MONDAY,
            dialogBinding.dayBtnTue to Calendar.TUESDAY,
            dialogBinding.dayBtnWed to Calendar.WEDNESDAY,
            dialogBinding.dayBtnThu to Calendar.THURSDAY,
            dialogBinding.dayBtnFri to Calendar.FRIDAY,
            dialogBinding.dayBtnSat to Calendar.SATURDAY
        ).forEach { (btn, day) ->
            btn.setOnClickListener {
                if (day in selectedDays) selectedDays.remove(day) else selectedDays.add(day)
                updateDaysUI()
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnReminderCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnReminderSave.setOnClickListener {
            val title = dialogBinding.etReminderTitle.text.toString().trim()
            val message = dialogBinding.etReminderMessage.text.toString().trim()

            if (title.isEmpty()) {
                requireContext().showToast(R.string.reminder_title_required)
                return@setOnClickListener
            }
            if (selectedType == Reminder.Type.WEEKLY && selectedDays.isEmpty()) {
                requireContext().showToast(R.string.reminder_days_required)
                return@setOnClickListener
            }
            if (!ReminderScheduler.canScheduleExact(requireContext())) {
                requireContext().showToast(R.string.reminder_exact_alarm_needed)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }

            val id = existing?.id ?: ((reminders.maxOfOrNull { it.id } ?: 0) + 1)
            val reminder = Reminder(
                id = id,
                title = title,
                message = message,
                type = selectedType,
                hour = selectedHour,
                minute = selectedMinute,
                intervalHours = selectedInterval,
                days = selectedDays.toSet(),
                enabled = existing?.enabled ?: true,
                fireCount = existing?.fireCount ?: 0,
                doneCount = existing?.doneCount ?: 0
            )

            if (isEdit) {
                updateReminder(reminder)
            } else {
                reminders.add(reminder)
                saveAll()
            }

            ReminderScheduler.schedule(requireContext(), reminder)
            refreshList()
            requireContext().showToast(if (isEdit) R.string.reminder_updated else R.string.reminder_added)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateReminder(reminder: Reminder) {
        val index = reminders.indexOfFirst { it.id == reminder.id }
        if (index >= 0) reminders[index] = reminder
        saveAll()
    }

    private fun saveAll() {
        prefs.remindersJson = reminders.toJsonString()
    }
}
