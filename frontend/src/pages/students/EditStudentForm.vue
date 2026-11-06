<template>
  <el-form
    ref="studentForm"
    :model="studentBeingEdited"
    :rules="rules"
    label-width="110px"
    style="padding-right: 30px"
    label-position="left"
  >
    <el-form-item label="Id:">
      <el-input v-model="studentBeingEdited.id" disabled></el-input>
    </el-form-item>
    <el-form-item label="Username:" prop="username">
      <el-input v-model="studentBeingEdited.username"></el-input>
    </el-form-item>
    <el-form-item label="Status:">
      <el-tag v-if="loadedStudent" :type="loadedStudent.enabled ? 'success' : 'info'" data-cy="student-status">
        {{ loadedStudent.enabled ? 'Active' : 'Deactivated' }}
      </el-tag>
    </el-form-item>
    <el-form-item label="First Name:" prop="firstName">
      <el-input v-model="studentBeingEdited.firstName" data-cy="student-first-name"></el-input>
    </el-form-item>
    <el-form-item label="Last Name:" prop="lastName">
      <el-input v-model="studentBeingEdited.lastName"></el-input>
    </el-form-item>
    <el-form-item label="Email:" prop="email">
      <el-input v-model="studentBeingEdited.email"></el-input>
    </el-form-item>
    <el-form-item label="Team:" prop="teamName">
      <el-input v-model="studentBeingEdited.teamName" disabled></el-input>
    </el-form-item>
    <div style="display: flex; justify-content: end">
      <el-button
        v-if="loadedStudent?.enabled"
        type="danger"
        data-cy="deactivate-student"
        @click="deactivateStudent()"
      >
        Deactivate
      </el-button>
      <el-button @click="$emit('close-dialog')"> Cancel </el-button>
      <el-button type="primary" @click="updateExistingStudent()"> Confirm </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Student } from '@/apis/student/types'
import type { FormInstance } from 'element-plus'
import { findStudentById, updateStudent } from '@/apis/student'
import { ref, onMounted } from 'vue'

const { studentId } = defineProps(['studentId'])
const emit = defineEmits(['close-dialog'])

// Note that the structure of the userData is NOT the same as the User interface since the roles are stored as an array
const studentBeingEdited = ref<Student>({
  // Data of the user being added or edited
  id: NaN,
  username: '',
  firstName: '',
  lastName: '',
  email: ''
})

// The record as it was loaded when the dialog opened. Deactivate sends this, not unsaved edits in the form,
// which are discarded when the dialog closes (UC-STU-deactivate-student, design/stu.md).
const loadedStudent = ref<Student>()

const studentForm = ref<FormInstance>() // Form reference

// Validation rules
const rules = {
  username: [
    { required: true, message: 'Please input your email as your username', trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: 'Enter your email as username.',
      trigger: 'blur'
    }
  ],
  firstName: [
    { required: true, message: 'Please provide the first name of the user.', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Please provide the last name of the user.', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Please provide the email of the user.', trigger: 'blur' },
    { type: 'email', message: 'Please provide a valid email.', trigger: 'blur' }
  ]
}

async function loadStudent(studentId: number) {
  // Load the user to be edited
  const result = await findStudentById(studentId)

  loadedStudent.value = { ...result.data }
  studentBeingEdited.value.id = result.data.id as number
  studentBeingEdited.value.username = result.data.username
  studentBeingEdited.value.firstName = result.data.firstName
  studentBeingEdited.value.lastName = result.data.lastName
  studentBeingEdited.value.email = result.data.email
  studentBeingEdited.value.teamName = result.data.teamName
}

onMounted(() => {
  loadStudent(studentId) // Load the artifact to be edited
})

async function updateExistingStudent() {
  await studentForm.value!.validate() // Validate the form

  // No enabled: a profile save leaves the account status as it is on the server, so a dialog opened before
  // someone else deactivated the student cannot quietly reactivate her
  const updatedStudent: Student = {
    id: studentBeingEdited.value.id,
    username: studentBeingEdited.value.username,
    firstName: studentBeingEdited.value.firstName,
    lastName: studentBeingEdited.value.lastName,
    email: studentBeingEdited.value.email
  }
  // Call the API to update the user
  await updateStudent(updatedStudent)

  ElMessage.success('Student updated successfully')
  emit('close-dialog')
}

// UC-STU-deactivate-student steps 3-6
async function deactivateStudent() {
  const student = loadedStudent.value!
  const name = `${student.firstName} ${student.lastName}`

  try {
    // Step 4: the consequences, a warning, and a request for confirmation
    await ElMessageBox.confirm(
      `${name} will no longer be able to log in or submit weekly activity reports and peer evaluations. ` +
        'Her account, her submitted work, and her course section and team membership are kept, and her ' +
        'teammates will no longer owe or be able to submit a peer evaluation of her. She can be reactivated ' +
        'later. This is not a deletion.',
      `Deactivate ${name}?`,
      { type: 'warning', confirmButtonText: 'Deactivate', cancelButtonText: 'Cancel' }
    )
  } catch {
    return // 5a: cancelled; nothing is sent and the details dialog stays open
  }

  // Step 5 confirmed. 6a (the student no longer exists) comes back as a 403, which the shared client shows as
  // an error toast and the Forbidden page; the rejected promise stops here.
  const result = await updateStudent({
    id: student.id,
    username: student.username,
    firstName: student.firstName,
    lastName: student.lastName,
    email: student.email,
    enabled: false
  })

  // Step 6
  ElMessage.success(`${result.data.firstName} ${result.data.lastName} has been deactivated.`)
  emit('close-dialog')
}
</script>

<style scoped lang="scss"></style>
