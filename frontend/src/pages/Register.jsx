import { useState, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

function Register() {
    const [formData, setFormData] = useState({ firstName: '', lastName: '', email: '', password: '' });
    const [error, setError] = useState('');
    const { register } = useContext(AuthContext);
    const navigate = useNavigate();

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            await register(formData);
            navigate('/');
        } catch (err) {
            setError('Error registering. Please check your details and try again.');
        }
    };

    return (
        <div className="flex justify-center items-center mt-20">
            <div className="bg-white p-8 rounded-lg shadow-lg w-full max-w-md border">
                <h2 className="text-3xl font-bold text-center text-green-600 mb-6">Create Account</h2>

                {error && <p className="bg-red-100 text-red-600 p-3 rounded mb-4 text-center">{error}</p>}

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div>
                        <label className="block text-gray-700 font-semibold mb-2">First Name</label>
                        <input
                            type="text" name="firstName"
                            className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-green-500"
                            value={formData.firstName} onChange={handleChange} required
                        />
                    </div>

                    <div>
                        <label className="block text-gray-700 font-semibold mb-2">Last Name</label>
                        <input
                            type="text" name="lastName"
                            className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-green-500"
                            value={formData.lastName} onChange={handleChange} required
                        />
                    </div>

                    <div>
                        <label className="block text-gray-700 font-semibold mb-2">Email</label>
                        <input
                            type="email" name="email"
                            className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-green-500"
                            value={formData.email} onChange={handleChange} required
                        />
                    </div>

                    <div>
                        <label className="block text-gray-700 font-semibold mb-2">Password</label>
                        <input
                            type="password" name="password"
                            className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-green-500"
                            value={formData.password} onChange={handleChange} required
                        />
                    </div>

                    <button type="submit" className="bg-green-600 text-white font-bold p-3 rounded hover:bg-green-700 transition mt-2">
                        Register
                    </button>
                </form>

                <p className="mt-4 text-center text-gray-600">
                    Already have an account? <Link to="/login" className="text-green-500 hover:underline">Sign In</Link>
                </p>
            </div>
        </div>
    );
}

export default Register;