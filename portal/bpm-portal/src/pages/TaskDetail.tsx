import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { completeTask, getFormById, getTaskById } from '../api/bpmApi';
import FormRenderer from '../components/FormRenderer';

const TaskDetail: React.FC = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [task, setTask] = useState<any>(null);
  const [formSchema, setFormSchema] = useState<any>(null);

  useEffect(() => {
    if (!id) return;
    getTaskById(Number(id)).then(res => {
      setTask(res.data);
      if (res.data.formId) {
        getFormById(res.data.formId).then(resp => setFormSchema(resp.data.schema));
      }
    });
  }, [id]);

  const handleSubmit = (formData: any) => {
    completeTask(Number(id), {
      assignee: 'web-user', // ou input do usuário
      variables: formData,
    }).then(() => {
      alert('Task completed');
      navigate('/tasks');
    });
  };

  if (!task) return <div>Loading...</div>;

  return (
    <div className="container mt-4">
      <h2>Task Detail</h2>
      {formSchema ? (
        <FormRenderer schema={formSchema} onSubmit={handleSubmit} />
      ) : (
        <div>No form available for this task.</div>
      )}
    </div>
  );
};

export default TaskDetail;
