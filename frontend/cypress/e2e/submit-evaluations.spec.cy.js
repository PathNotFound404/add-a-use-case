// UC-STU-deactivate-student / UC-EVA-submit-evaluation step 2: a deactivated teammate is not listed on the submit
// page (BR-submission-owed). The team roster and this week's evaluations are stubbed; the backend's own tests
// cover that a POST for her is refused. Run against the dev stack with `npm run cy:open`.

const teammate = (id, firstName, lastName, enabled) => ({
  id,
  username: `${firstName.toLowerCase()}@abc.edu`,
  firstName,
  lastName,
  email: `${firstName.toLowerCase()}@abc.edu`,
  enabled,
  roles: 'student',
  teamId: 1,
  teamName: 'Team1'
})

describe('Submit peer evaluations with a deactivated teammate', () => {
  it('lists only the active team members', () => {
    cy.intercept('GET', '**/students/teams/*', {
      body: {
        flag: true,
        code: 200,
        message: 'Find students by team id successfully',
        data: [
          teammate(4, 'John', 'Smith', true),
          teammate(5, 'Eric', 'Hudson', false), // Deactivated
          teammate(6, 'Jerry', 'Moon', true)
        ]
      }
    }).as('team')
    cy.intercept('GET', '**/evaluations/evaluators/*/week/*', {
      body: { flag: true, code: 200, message: 'Find evaluations successfully', data: [] }
    }).as('submitted')

    cy.login('j.smith@abc.edu', '123456')
    cy.visit('/evaluations/submit-evaluations')
    cy.wait(['@team', '@submitted'])

    cy.get('.el-table').should('contain', 'John Smith').and('contain', 'Jerry Moon')
    cy.get('.el-table').should('not.contain', 'Eric Hudson')
  })
})
