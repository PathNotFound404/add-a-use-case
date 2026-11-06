// UC-STU-deactivate-student: the Deactivate action in the student details dialog. The student API calls are
// stubbed, so these tests check what the dialog shows and what it sends; the backend's own tests cover who may
// deactivate and what is kept. Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const john = {
  id: 4,
  username: 'j.smith@abc.edu',
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  enabled: true,
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
}

const envelope = (message, data) => ({ flag: true, code: 200, message, data })

const page = (students) =>
  envelope('Find students successfully', {
    content: students,
    totalElements: students.length,
    totalPages: 1,
    size: 10,
    number: 0
  })

describe('Deactivate a student', () => {
  function openDetails(student) {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/search*', { body: page([student]) }).as('search')
    cy.intercept('GET', `**/students/${student.id}`, { body: envelope('Find student successfully', student) }).as('find')
    cy.intercept('PUT', `**/students/${student.id}`, (req) => {
      req.reply({ body: envelope('Update student successfully', { ...student, ...req.body }) })
    }).as('update')
    cy.visit('/students')
    cy.wait('@search')
    cy.get('[data-cy=edit-student]').first().click()
    cy.wait('@find')
  }

  it('offers Deactivate only for an active student (PRE-2)', () => {
    openDetails({ ...john, enabled: false })

    cy.get('[data-cy=student-status]').should('have.text', 'Deactivated')
    cy.get('[data-cy=deactivate-student]').should('not.exist')
  })

  it('warns with the consequences and sends nothing when cancelled (steps 3-4, 5a)', () => {
    openDetails(john)

    cy.get('[data-cy=student-status]').should('have.text', 'Active')
    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box')
      .should('contain', 'Deactivate John Smith?')
      .and('contain', 'will no longer be able to log in or submit weekly activity reports and peer evaluations')
      .and('contain', 'course section and team membership are kept')
      .and('contain', 'This is not a deletion.')
    cy.screenshot('deactivate-confirmation') // The confirmation dialog, for the pull request
    cy.get('.el-message-box').contains('button', 'Cancel').click()

    cy.get('.el-message-box').should('not.exist')
    cy.get('[data-cy=deactivate-student]').should('be.visible') // The details dialog stays open
    cy.get('@update.all').should('have.length', 0)
  })

  it('sends enabled: false with the record as loaded, then confirms (steps 5-6)', () => {
    openDetails(john)

    cy.get('[data-cy=student-first-name]').clear().type('Unsaved edit') // A first-name edit that Deactivate must not send
    cy.get('[data-cy=deactivate-student]').click()
    cy.get('.el-message-box').contains('button', 'Deactivate').click()

    cy.wait('@update').its('request.body').should('deep.equal', {
      id: 4,
      username: 'j.smith@abc.edu',
      firstName: 'John',
      lastName: 'Smith',
      email: 'j.smith@abc.edu',
      enabled: false
    })
    cy.get('.el-message--success').should('contain', 'John Smith has been deactivated.')
  })

  it('sends no enabled when the profile is saved', () => {
    openDetails(john)

    cy.contains('button', 'Confirm').click()

    cy.wait('@update').its('request.body').should('not.have.property', 'enabled')
  })
})
