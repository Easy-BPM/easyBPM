import type { DecisionTable } from '../decisionTypes';

export interface DecisionExample {
  title: string;
  description: string;
  table: DecisionTable;
  inputs: string;
  expected: string;
  testName: string;
}

export const decisionExamples: DecisionExample[] = [
  {
    title: 'Eligibility', description: 'Check an age threshold. A simple introduction to UNIQUE matching.',
    table: { key: 'eligibility', name: 'Eligibility', hitPolicy: 'UNIQUE', inputs: [{ id: 'age', label: 'Age', expression: 'age', type: 'number' }], outputs: [{ id: 'eligible', name: 'eligible', type: 'boolean' }], rules: [{ id: 'adult', description: 'Adult applicant', inputEntries: ['>= 18'], outputEntries: ['true'] }, { id: 'minor', description: 'Minor applicant', inputEntries: ['< 18'], outputEntries: ['false'] }] },
    inputs: '{"age": 21}', expected: '{"eligible": true}', testName: 'Adult applicant'
  },
  {
    title: 'Expense approval', description: 'Route expenses by amount, using mutually exclusive rules.',
    table: { key: 'expenseApproval', name: 'Expense approval', hitPolicy: 'UNIQUE', inputs: [{ id: 'amount', label: 'Amount', expression: 'amount', type: 'number' }], outputs: [{ id: 'approval', name: 'approval', type: 'string' }, { id: 'review', name: 'requiresReview', type: 'boolean' }], rules: [{ id: 'automaticApproval', description: 'Small expense', inputEntries: ['< 1000'], outputEntries: ['"APPROVED"', 'false'] }, { id: 'managerReview', description: 'Manager approval required', inputEntries: ['>= 1000'], outputEntries: ['"REVIEW"', 'true'] }] },
    inputs: '{"amount": 250}', expected: '{"approval": "APPROVED", "requiresReview": false}', testName: 'Small expense'
  },
  {
    title: 'Discount collection', description: 'Collect every eligible benefit, keeping the order of matching rules.',
    table: { key: 'discounts', name: 'Discount collection', hitPolicy: 'COLLECT', inputs: [{ id: 'orderAmount', label: 'Order amount', expression: 'amount', type: 'number' }, { id: 'member', label: 'Member', expression: 'member', type: 'boolean' }], outputs: [{ id: 'benefit', name: 'benefit', type: 'string' }], rules: [{ id: 'largeOrder', description: 'Large order benefit', inputEntries: ['>= 100', '-'], outputEntries: ['"FREE_SHIPPING"'] }, { id: 'memberBenefit', description: 'Member benefit', inputEntries: ['-', 'true'], outputEntries: ['"MEMBER_DISCOUNT"'] }] },
    inputs: '{"amount": 150, "member": true}', expected: '[{"benefit":"FREE_SHIPPING"},{"benefit":"MEMBER_DISCOUNT"}]', testName: 'Member with a large order'
  }
];
